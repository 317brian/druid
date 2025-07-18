/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.druid.java.util.emitter.service;

import com.google.common.collect.ImmutableMap;
import org.apache.druid.java.util.common.DateTimes;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 *
 */
public class ServiceMetricEventTest
{
  @Test
  public void testBuilder()
  {
    ServiceMetricEvent builderEvent = new ServiceMetricEvent.Builder()
        .setDimension("user1", "a")
        .setDimension("user2", "b")
        .setDimension("user3", "c")
        .setDimension("user4", "d")
        .setDimension("user5", "e")
        .setDimension("user6", "f")
        .setDimension("user7", "g")
        .setDimension("user8", "h")
        .setDimension("user9", "i")
        .setDimension("user10", "j")
        .setMetric("test-metric", 1234)
        .build("test", "localhost");
    Assert.assertEquals(
        ImmutableMap.<String, Object>builder()
                    .put("feed", "metrics")
                    .put("timestamp", builderEvent.getCreatedTime().toString())
                    .put("service", "test")
                    .put("host", "localhost")
                    .put("metric", "test-metric")
                    .put("user1", "a")
                    .put("user2", "b")
                    .put("user3", "c")
                    .put("user4", "d")
                    .put("user5", "e")
                    .put("user6", "f")
                    .put("user7", "g")
                    .put("user8", "h")
                    .put("user9", "i")
                    .put("user10", "j")
                    .put("value", 1234)
                    .build(),
        builderEvent.toMap()
    );

    ServiceMetricEvent constructorEvent = ServiceMetricEvent
        .builder()
        .setDimension("user1", "a")
        .setDimension("user2", "b")
        .setDimension("user3", "c")
        .setDimension("user4", "d")
        .setDimension("user5", "e")
        .setDimension("user6", "f")
        .setDimension("user7", "g")
        .setDimension("user8", "h")
        .setDimension("user9", "i")
        .setDimension("user10", "j")
        .setMetric("test-metric", 1234)
        .build("test", "localhost");

    Assert.assertEquals(
        ImmutableMap.<String, Object>builder()
                    .put("feed", "metrics")
                    .put("timestamp", constructorEvent.getCreatedTime().toString())
                    .put("service", "test")
                    .put("host", "localhost")
                    .put("metric", "test-metric")
                    .put("user1", "a")
                    .put("user2", "b")
                    .put("user3", "c")
                    .put("user4", "d")
                    .put("user5", "e")
                    .put("user6", "f")
                    .put("user7", "g")
                    .put("user8", "h")
                    .put("user9", "i")
                    .put("user10", "j")
                    .put("value", 1234)
                    .build(), constructorEvent.toMap()
    );

    ServiceMetricEvent arrayConstructorEvent = ServiceMetricEvent
        .builder()
        .setDimension("user1", new String[]{"a"})
        .setDimension("user2", new String[]{"b"})
        .setDimension("user3", new String[]{"c"})
        .setDimension("user4", new String[]{"d"})
        .setDimension("user5", new String[]{"e"})
        .setDimension("user6", new String[]{"f"})
        .setDimension("user7", new String[]{"g"})
        .setDimension("user8", new String[]{"h"})
        .setDimension("user9", new String[]{"i"})
        .setDimension("user10", new String[]{"j"})
        .setMetric("test-metric", 1234)
        .build("test", "localhost");

    Assert.assertEquals(
        ImmutableMap.<String, Object>builder()
                    .put("feed", "metrics")
                    .put("timestamp", arrayConstructorEvent.getCreatedTime().toString())
                    .put("service", "test")
                    .put("host", "localhost")
                    .put("metric", "test-metric")
                    .put("user1", Collections.singletonList("a"))
                    .put("user2", Collections.singletonList("b"))
                    .put("user3", Collections.singletonList("c"))
                    .put("user4", Collections.singletonList("d"))
                    .put("user5", Collections.singletonList("e"))
                    .put("user6", Collections.singletonList("f"))
                    .put("user7", Collections.singletonList("g"))
                    .put("user8", Collections.singletonList("h"))
                    .put("user9", Collections.singletonList("i"))
                    .put("user10", Collections.singletonList("j"))
                    .put("value", 1234)
                    .build(), arrayConstructorEvent.toMap()
    );

    Assert.assertNotNull(
        new ServiceMetricEvent.Builder()
            .setDimension("user1", "a")
            .setDimension("user2", "b")
            .setDimension("user3", "c")
            .setDimension("user4", "d")
            .setDimension("user5", "e")
            .setDimension("user6", "f")
            .setDimension("user7", "g")
            .setDimension("user8", "h")
            .setDimension("user9", "i")
            .setDimension("user10", "j")
            .setMetric("test-metric", 1234)
            .build("test", "localhost")
            .getCreatedTime()
    );

    Assert.assertNotNull(
        ServiceMetricEvent.builder()
                          .setDimension("user1", new String[]{"a"})
                          .setDimension("user2", new String[]{"b"})
                          .setDimension("user3", new String[]{"c"})
                          .setDimension("user4", new String[]{"d"})
                          .setDimension("user5", new String[]{"e"})
                          .setDimension("user6", new String[]{"f"})
                          .setDimension("user7", new String[]{"g"})
                          .setDimension("user8", new String[]{"h"})
                          .setDimension("user9", new String[]{"i"})
                          .setDimension("user10", new String[]{"j"})
                          .setMetric("test-metric", 1234)
                          .build("test", "localhost")
                          .getCreatedTime()
    );

    Assert.assertEquals(
        ImmutableMap.<String, Object>builder()
                    .put("feed", "metrics")
                    .put("timestamp", DateTimes.utc(42).toString())
                    .put("service", "test")
                    .put("host", "localhost")
                    .put("metric", "test-metric")
                    .put("user1", "a")
                    .put("user2", "b")
                    .put("user3", "c")
                    .put("user4", "d")
                    .put("user5", "e")
                    .put("user6", "f")
                    .put("user7", "g")
                    .put("user8", "h")
                    .put("user9", "i")
                    .put("user10", "j")
                    .put("value", 1234)
                    .build(),
        new ServiceMetricEvent.Builder()
            .setDimension("user1", "a")
            .setDimension("user2", "b")
            .setDimension("user3", "c")
            .setDimension("user4", "d")
            .setDimension("user5", "e")
            .setDimension("user6", "f")
            .setDimension("user7", "g")
            .setDimension("user8", "h")
            .setDimension("user9", "i")
            .setDimension("user10", "j")
            .setCreatedTime(DateTimes.utc(42))
            .setMetric("test-metric", 1234)
            .build("test", "localhost")
            .toMap()
    );

    Assert.assertEquals(
        ImmutableMap.<String, Object>builder()
                    .put("feed", "metrics")
                    .put("timestamp", DateTimes.utc(42).toString())
                    .put("service", "test")
                    .put("host", "localhost")
                    .put("metric", "test-metric")
                    .put("user1", Collections.singletonList("a"))
                    .put("user2", Collections.singletonList("b"))
                    .put("user3", Collections.singletonList("c"))
                    .put("user4", Collections.singletonList("d"))
                    .put("user5", Collections.singletonList("e"))
                    .put("user6", Collections.singletonList("f"))
                    .put("user7", Collections.singletonList("g"))
                    .put("user8", Collections.singletonList("h"))
                    .put("user9", Collections.singletonList("i"))
                    .put("user10", Collections.singletonList("j"))
                    .put("value", 1234)
                    .build(),
        ServiceMetricEvent.builder()
                          .setDimension("user1", new String[]{"a"})
                          .setDimension("user2", new String[]{"b"})
                          .setDimension("user3", new String[]{"c"})
                          .setDimension("user4", new String[]{"d"})
                          .setDimension("user5", new String[]{"e"})
                          .setDimension("user6", new String[]{"f"})
                          .setDimension("user7", new String[]{"g"})
                          .setDimension("user8", new String[]{"h"})
                          .setDimension("user9", new String[]{"i"})
                          .setDimension("user10", new String[]{"j"})
                          .setCreatedTime(DateTimes.utc(42))
                          .setMetric("test-metric", 1234)
                          .build("test", "localhost")
                          .toMap()
    );

    Assert.assertEquals(
        ImmutableMap.<String, Object>builder()
                    .put("feed", "metrics")
                    .put("timestamp", DateTimes.utc(42).toString())
                    .put("service", "test")
                    .put("host", "localhost")
                    .put("metric", "test-metric")
                    .put("foo", "bar")
                    .put("baz", Arrays.asList("foo", "qux"))
                    .put("value", 1234)
                    .build(),
        ServiceMetricEvent.builder()
                          .setDimension("foo", "bar")
                          .setDimension("baz", new String[]{"foo", "qux"})
                          .setCreatedTime(DateTimes.utc(42))
                          .setMetric("test-metric", 1234)
                          .build("test", "localhost")
                          .toMap()
    );
  }

  @Test(expected = IllegalStateException.class)
  public void testInfinite()
  {
    ServiceMetricEvent.builder().setMetric("foo", 1 / 0d);
  }

  @Test(expected = IllegalStateException.class)
  public void testInfinite2()
  {
    ServiceMetricEvent.builder().setMetric("foo", 1 / 0f);
  }


  @Test(expected = IllegalStateException.class)
  public void testNaN()
  {
    ServiceMetricEvent.builder().setMetric("foo", 0 / 0d);
  }

  @Test(expected = IllegalStateException.class)
  public void testNaN2()
  {
    ServiceMetricEvent.builder().setMetric("foo", 0 / 0f);
  }

  @Test
  public void testSetDimensionIfNotNullSetsNonNullDimension()
  {
    Map<String, String> userDimMap = ImmutableMap.of("k1", "v1", "k2", "v2");
    ServiceMetricEvent target = ServiceMetricEvent.builder()
                                                  .setDimensionIfNotNull("userDimMap", userDimMap)
                                                  .setMetric("foo", 1)
                                                  .build("service", "host");
    Assert.assertEquals(userDimMap, target.getUserDims().get("userDimMap"));
  }

  @Test
  public void testSetDimensionIfNotNullShouldNotSetNullDimension()
  {
    ServiceMetricEvent target = ServiceMetricEvent.builder()
                                                  .setDimensionIfNotNull("userDimMap", null)
                                                  .setMetric("foo", 1)
                                                  .build("service", "host");
    Assert.assertTrue(target.getUserDims().isEmpty());
    Assert.assertNull(target.getUserDims().get("userDimMap"));
  }

  @Test
  public void test_copy_returnsAnImmutableInstance()
  {
    final ServiceMetricEvent.Builder eventBuilder = ServiceMetricEvent
        .builder()
        .setDimension("dim1", "v1")
        .setMetric("m1", 100);

    final ServiceMetricEvent event1 = eventBuilder.build("coordinator", "localhost");
    final ServiceMetricEvent event1Copy = event1.copy();

    Assert.assertEquals(Map.of("dim1", "v1"), event1.getUserDims());
    Assert.assertEquals(Map.of("dim1", "v1"), event1Copy.getUserDims());

    final ServiceMetricEvent event2 = eventBuilder
        .setDimension("dim2", "v2")
        .setMetric("m2", 200)
        .build("coordinator", "localhost");

    // Verify that the original event gets changed dimensions
    Assert.assertEquals(Map.of("dim1", "v1", "dim2", "v2"), event2.getUserDims());
    Assert.assertEquals(Map.of("dim1", "v1", "dim2", "v2"), event1.getUserDims());

    // But the event copy still has the original dimensions
    Assert.assertEquals(Map.of("dim1", "v1"), event1Copy.getUserDims());
  }
}
