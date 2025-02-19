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
package org.apache.iotdb.db.metadata;

import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.metadata.mnode.InternalMNode;
import org.apache.iotdb.db.metadata.path.AlignedPath;
import org.apache.iotdb.db.metadata.path.MeasurementPath;
import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.metadata.utils.MetaUtils;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MetaUtilsTest {

  @Test
  public void testSplitPathToNodes() throws IllegalPathException {
    assertArrayEquals(
        Arrays.asList("root", "sg", "d1", "s1").toArray(),
        MetaUtils.splitPathToDetachedPath("root.sg.d1.s1"));

    assertArrayEquals(
        Arrays.asList("root", "sg", "d1", "\"s+1\"").toArray(),
        MetaUtils.splitPathToDetachedPath("root.sg.d1.\"s+1\""));

    assertArrayEquals(
        Arrays.asList("root", "sg", "d1", "\"s\\\"+-1\"").toArray(),
        MetaUtils.splitPathToDetachedPath("root.sg.d1.\"s\\\"+-1\""));

    assertArrayEquals(
        Arrays.asList("root", "\"s g\"", "d1", "\"s+1\"").toArray(),
        MetaUtils.splitPathToDetachedPath("root.\"s g\".d1.\"s+1\""));

    assertArrayEquals(
        Arrays.asList("root", "\"s g\"", "\"d_+-1\"", "\"s+1+2\"").toArray(),
        MetaUtils.splitPathToDetachedPath("root.\"s g\".\"d_+-1\".\"s+1+2\""));

    assertArrayEquals(
        Arrays.asList("root", "1").toArray(), MetaUtils.splitPathToDetachedPath("root.1"));

    assertArrayEquals(
        Arrays.asList("root", "sg", "d1", "s", "1").toArray(),
        MetaUtils.splitPathToDetachedPath("root.sg.d1.s.1"));

    try {
      MetaUtils.splitPathToDetachedPath("root..a");
      fail();
    } catch (IllegalPathException e) {
      Assert.assertEquals("root..a is not a legal path", e.getMessage());
    }

    try {
      MetaUtils.splitPathToDetachedPath("root.sg.d1.");
      fail();
    } catch (IllegalPathException e) {
      Assert.assertEquals("root.sg.d1. is not a legal path", e.getMessage());
    }
  }

  @Test
  public void testGetMultiFullPaths() {
    InternalMNode rootNode = new InternalMNode(null, "root");

    // builds the relationship of root.a and root.aa
    InternalMNode aNode = new InternalMNode(rootNode, "a");
    rootNode.addChild(aNode.getName(), aNode);
    InternalMNode aaNode = new InternalMNode(rootNode, "aa");
    rootNode.addChild(aaNode.getName(), aaNode);

    // builds the relationship of root.a.b and root.aa.bb
    InternalMNode bNode = new InternalMNode(aNode, "b");
    aNode.addChild(bNode.getName(), bNode);
    InternalMNode bbNode = new InternalMNode(aaNode, "bb");
    aaNode.addChild(bbNode.getName(), bbNode);

    // builds the relationship of root.aa.bb.cc
    InternalMNode ccNode = new InternalMNode(bbNode, "cc");
    bbNode.addChild(ccNode.getName(), ccNode);

    List<String> multiFullPaths = MetaUtils.getMultiFullPaths(rootNode);
    Assert.assertSame(2, multiFullPaths.size());

    multiFullPaths.forEach(
        fullPath -> {
          if (fullPath.contains("aa")) {
            Assert.assertEquals("root.aa.bb.cc", fullPath);
          } else {
            Assert.assertEquals("root.a.b", fullPath);
          }
        });
  }

  @Test
  public void testGroupAlignedPath() throws MetadataException {
    List<PartialPath> pathList = new ArrayList<>();

    MeasurementPath path1 = new MeasurementPath(new PartialPath("root.sg.device.s1"), null);
    pathList.add(path1);
    MeasurementPath path2 = new MeasurementPath(new PartialPath("root.sg.device.s2"), null);
    pathList.add(path2);

    MeasurementPath path3 = new MeasurementPath(new PartialPath("root.sg.aligned_device.s1"), null);
    path3.setUnderAlignedEntity(true);
    pathList.add(path3);
    MeasurementPath path4 = new MeasurementPath(new PartialPath("root.sg.aligned_device.s2"), null);
    path4.setUnderAlignedEntity(true);
    pathList.add(path4);

    AlignedPath alignedPath = new AlignedPath(path3);
    alignedPath.addMeasurement(path4);

    List<PartialPath> result = MetaUtils.groupAlignedPaths(pathList);
    assertTrue(result.contains(path1));
    assertTrue(result.contains(path2));
    assertTrue(result.contains(alignedPath));
  }
}
