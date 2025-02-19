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
package org.apache.iotdb.db.mpp.sql.planner.plan.node.process;

import org.apache.iotdb.commons.utils.TestOnly;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.PlanNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.PlanNodeId;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.PlanVisitor;
import org.apache.iotdb.tsfile.exception.NotImplementedException;
import org.apache.iotdb.tsfile.utils.Pair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This node is responsible for the final aggregation merge operation. It will process the data from
 * TsBlock row by row. For one row, it will rollup the fields which have the same aggregate function
 * and belong to one bucket. Here, that two columns belong to one bucket means the partial paths of
 * device after rolling up in specific level are the same. For example, let's say there are two
 * columns `root.sg.d1.s1` and `root.sg.d2.s1`. If the group by level parameter is [0, 1], then
 * these two columns will belong to one bucket and the bucket name is `root.sg.*.s1`. If the group
 * by level parameter is [0, 2], then these two columns will not belong to one bucket. And the total
 * buckets are `root.*.d1.s1` and `root.*.d2.s1`
 */
public class GroupByLevelNode extends ProcessNode {

  private PlanNode child;

  private int[] groupByLevels;

  private List<String> columnNames;

  private Map<String, String> groupedPathMap;

  public GroupByLevelNode(
      PlanNodeId id, PlanNode child, int[] groupByLevels, Map<String, String> groupedPathMap) {
    super(id);
    this.child = child;
    this.groupByLevels = groupByLevels;
    this.groupedPathMap = groupedPathMap;
    this.columnNames = new ArrayList<>(groupedPathMap.values());
  }

  @Override
  public List<PlanNode> getChildren() {
    return child.getChildren();
  }

  @Override
  public void addChild(PlanNode child) {
    throw new NotImplementedException("addChild of GroupByLevelNode is not implemented");
  }

  @Override
  public PlanNode clone() {
    throw new NotImplementedException("Clone of GroupByLevelNode is not implemented");
  }

  @Override
  public int allowedChildCount() {
    return CHILD_COUNT_NO_LIMIT;
  }

  @Override
  public List<String> getOutputColumnNames() {
    return columnNames;
  }

  @Override
  public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
    return visitor.visitGroupByLevel(this, context);
  }

  public static GroupByLevelNode deserialize(ByteBuffer byteBuffer) {
    return null;
  }

  @Override
  public void serialize(ByteBuffer byteBuffer) {}

  public int[] getGroupByLevels() {
    return groupByLevels;
  }

  public void setGroupByLevels(int[] groupByLevels) {
    this.groupByLevels = groupByLevels;
  }

  public List<String> getColumnNames() {
    return columnNames;
  }

  public void setColumnNames(List<String> columnNames) {
    this.columnNames = columnNames;
  }

  @TestOnly
  public Pair<String, List<String>> print() {
    String title = String.format("[GroupByLevelNode (%s)]", this.getPlanNodeId());
    List<String> attributes = new ArrayList<>();
    attributes.add("GroupByLevels: " + Arrays.toString(this.getGroupByLevels()));
    attributes.add("ColumnNames: " + this.getOutputColumnNames());
    return new Pair<>(title, attributes);
  }
}
