/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.mpp.common;

import org.apache.iotdb.mpp.rpc.thrift.TFragmentInstanceId;

/** The fragment instance ID class. */
public class FragmentInstanceId {

  private final String fullId;
  private final QueryId queryId;
  private final PlanFragmentId fragmentId;
  private final String instanceId;

  public FragmentInstanceId(PlanFragmentId fragmentId, String instanceId) {
    this.queryId = fragmentId.getQueryId();
    this.fragmentId = fragmentId;
    this.instanceId = instanceId;
    this.fullId =
        String.format("%s.%d.%s", fragmentId.getQueryId().getId(), fragmentId.getId(), instanceId);
  }

  public String getFullId() {
    return fullId;
  }

  public QueryId getQueryId() {
    return queryId;
  }

  public PlanFragmentId getFragmentId() {
    return fragmentId;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String toString() {
    return fullId;
  }

  public TFragmentInstanceId toThrift() {
    return new TFragmentInstanceId(queryId.getId(), String.valueOf(fragmentId.getId()), instanceId);
  }
}
