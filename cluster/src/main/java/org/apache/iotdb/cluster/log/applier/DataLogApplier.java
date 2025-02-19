/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.cluster.log.applier;

import org.apache.iotdb.cluster.ClusterIoTDB;
import org.apache.iotdb.cluster.exception.CheckConsistencyException;
import org.apache.iotdb.cluster.log.Log;
import org.apache.iotdb.cluster.log.logtypes.AddNodeLog;
import org.apache.iotdb.cluster.log.logtypes.CloseFileLog;
import org.apache.iotdb.cluster.log.logtypes.PhysicalPlanLog;
import org.apache.iotdb.cluster.log.logtypes.RemoveNodeLog;
import org.apache.iotdb.cluster.server.member.DataGroupMember;
import org.apache.iotdb.cluster.server.member.MetaGroupMember;
import org.apache.iotdb.cluster.utils.IOUtils;
import org.apache.iotdb.db.engine.StorageEngine;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.metadata.PathNotExistException;
import org.apache.iotdb.db.exception.metadata.StorageGroupNotSetException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.qp.physical.PhysicalPlan;
import org.apache.iotdb.db.qp.physical.crud.*;
import org.apache.iotdb.db.qp.physical.crud.InsertMultiTabletsPlan;
import org.apache.iotdb.db.qp.physical.sys.DeleteTimeSeriesPlan;
import org.apache.iotdb.db.service.IoTDB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DataLogApplier applies logs like data insertion/deletion/update and timeseries creation to IoTDB.
 */
public class DataLogApplier extends BaseApplier {

  private static final Logger logger = LoggerFactory.getLogger(DataLogApplier.class);

  protected DataGroupMember dataGroupMember;

  public DataLogApplier(MetaGroupMember metaGroupMember, DataGroupMember dataGroupMember) {
    super(metaGroupMember);
    this.dataGroupMember = dataGroupMember;
  }

  @Override
  public void apply(Log log) {
    logger.debug("DataMember [{}] start applying Log {}", dataGroupMember.getName(), log);

    try {
      if (log instanceof AddNodeLog) {
        ClusterIoTDB.getInstance()
            .getDataGroupEngine()
            .preAddNodeForDataGroup((AddNodeLog) log, dataGroupMember);
        dataGroupMember.setAndSaveLastAppliedPartitionTableVersion(
            ((AddNodeLog) log).getMetaLogIndex());
      } else if (log instanceof RemoveNodeLog) {
        ClusterIoTDB.getInstance()
            .getDataGroupEngine()
            .preRemoveNodeForDataGroup((RemoveNodeLog) log, dataGroupMember);
        dataGroupMember.setAndSaveLastAppliedPartitionTableVersion(
            ((RemoveNodeLog) log).getMetaLogIndex());
      } else if (log instanceof PhysicalPlanLog) {
        PhysicalPlanLog physicalPlanLog = (PhysicalPlanLog) log;
        PhysicalPlan plan = physicalPlanLog.getPlan();
        applyPhysicalPlan(plan);
      } else if (log instanceof CloseFileLog) {
        CloseFileLog closeFileLog = ((CloseFileLog) log);
        StorageEngine.getInstance()
            .closeStorageGroupProcessor(
                new PartialPath(closeFileLog.getStorageGroupName()),
                closeFileLog.getPartitionId(),
                closeFileLog.isSeq(),
                false);
      } else {
        logger.error("Unsupported log: {}", log);
      }
    } catch (Exception e) {
      Throwable rootCause = IOUtils.getRootCause(e);
      if (!(rootCause instanceof PathNotExistException)) {
        logger.debug("Exception occurred when applying {}", log, e);
      }
      log.setException(e);
    } finally {
      log.setApplied(true);
    }
  }

  public void applyPhysicalPlan(PhysicalPlan plan)
      throws QueryProcessException, StorageGroupNotSetException, StorageEngineException {
    if (plan instanceof DeletePlan) {
      ((DeletePlan) plan).setPartitionFilter(dataGroupMember.getTimePartitionFilter());
    } else if (plan instanceof DeleteTimeSeriesPlan) {
      ((DeleteTimeSeriesPlan) plan).setPartitionFilter(dataGroupMember.getTimePartitionFilter());
    }
    if (plan instanceof InsertMultiTabletsPlan) {
      applyInsert((InsertMultiTabletsPlan) plan);
    } else if (plan instanceof InsertRowsPlan) {
      applyInsert((InsertRowsPlan) plan);
    } else if (plan instanceof InsertPlan) {
      applyInsert((InsertPlan) plan);
    } else {
      applyPhysicalPlan(plan, dataGroupMember);
    }
  }

  private void applyInsert(InsertMultiTabletsPlan plan)
      throws StorageGroupNotSetException, QueryProcessException, StorageEngineException {
    boolean hasSync = false;
    for (InsertTabletPlan insertTabletPlan : plan.getInsertTabletPlanList()) {
      try {
        IoTDB.schemaProcessor.getBelongedStorageGroup(insertTabletPlan.getDevicePath());
      } catch (StorageGroupNotSetException e) {
        try {
          if (!hasSync) {
            metaGroupMember.syncLeaderWithConsistencyCheck(true);
            hasSync = true;
          } else {
            throw new StorageEngineException(e.getMessage());
          }
        } catch (CheckConsistencyException ce) {
          throw new QueryProcessException(ce.getMessage());
        }
      }
    }
    applyPhysicalPlan(plan, dataGroupMember);
  }

  private void applyInsert(InsertRowsPlan plan)
      throws StorageGroupNotSetException, QueryProcessException, StorageEngineException {
    boolean hasSync = false;
    for (InsertRowPlan insertRowPlan : plan.getInsertRowPlanList()) {
      try {
        IoTDB.schemaProcessor.getBelongedStorageGroup(insertRowPlan.getDevicePath());
      } catch (StorageGroupNotSetException e) {
        try {
          if (!hasSync) {
            metaGroupMember.syncLeaderWithConsistencyCheck(true);
            hasSync = true;
          } else {
            throw new StorageEngineException(e.getMessage());
          }
        } catch (CheckConsistencyException ce) {
          throw new QueryProcessException(ce.getMessage());
        }
      }
    }
    applyPhysicalPlan(plan, dataGroupMember);
  }

  private void applyInsert(InsertPlan plan)
      throws StorageGroupNotSetException, QueryProcessException, StorageEngineException {
    try {
      IoTDB.schemaProcessor.getBelongedStorageGroup(plan.getDevicePath());
    } catch (StorageGroupNotSetException e) {
      // the sg may not exist because the node does not catch up with the leader, retry after
      // synchronization
      try {
        metaGroupMember.syncLeaderWithConsistencyCheck(true);
      } catch (CheckConsistencyException ce) {
        throw new QueryProcessException(ce.getMessage());
      }
    }
    applyPhysicalPlan(plan, dataGroupMember);
  }
}
