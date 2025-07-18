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

package org.apache.druid.client.selector;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import org.apache.druid.client.QueryableDruidServer;
import org.apache.druid.query.Query;
import org.apache.druid.timeline.DataSegment;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "tier", defaultImpl = HighestPriorityTierSelectorStrategy.class)
@JsonSubTypes(value = {
    @JsonSubTypes.Type(name = "highestPriority", value = HighestPriorityTierSelectorStrategy.class),
    @JsonSubTypes.Type(name = "lowestPriority", value = LowestPriorityTierSelectorStrategy.class),
    @JsonSubTypes.Type(name = "custom", value = CustomTierSelectorStrategy.class),
    @JsonSubTypes.Type(name = "preferred", value = PreferredTierSelectorStrategy.class),
})
public interface TierSelectorStrategy
{
  Comparator<Integer> getComparator();

  @Deprecated
  @Nullable
  default QueryableDruidServer pick(Int2ObjectRBTreeMap<Set<QueryableDruidServer>> prioritizedServers, DataSegment segment)
  {
    return pick(null, prioritizedServers, segment);
  }

  @Deprecated
  default List<QueryableDruidServer> pick(
      Int2ObjectRBTreeMap<Set<QueryableDruidServer>> prioritizedServers,
      DataSegment segment,
      int numServersToPick)
  {
    return pick(null, prioritizedServers, segment, numServersToPick);
  }

  @Nullable
  default <T> QueryableDruidServer pick(@Nullable Query<T> query,
      Int2ObjectRBTreeMap<Set<QueryableDruidServer>> prioritizedServers,
      DataSegment segment)
  {
    return pick(prioritizedServers, segment);
  }

  default <T> List<QueryableDruidServer> pick(
      @Nullable Query<T> query,
      Int2ObjectRBTreeMap<Set<QueryableDruidServer>> prioritizedServers,
      DataSegment segment,
      int numServersToPick)
  {
    return pick(prioritizedServers, segment, numServersToPick);
  }
}
