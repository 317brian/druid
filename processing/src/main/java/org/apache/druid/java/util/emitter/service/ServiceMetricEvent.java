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

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.druid.guice.annotations.PublicApi;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.java.util.common.ISE;
import org.apache.druid.java.util.emitter.core.Event;
import org.apache.druid.java.util.emitter.core.EventMap;
import org.joda.time.DateTime;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 */
@PublicApi
public class ServiceMetricEvent implements Event
{
  public static Builder builder()
  {
    return new Builder();
  }

  private final DateTime createdTime;
  private final ImmutableMap<String, String> serviceDims;
  private final Map<String, Object> userDims;
  private final String feed;
  private final String metric;
  private final Number value;

  private ServiceMetricEvent(
      DateTime createdTime,
      ImmutableMap<String, String> serviceDims,
      Map<String, Object> userDims,
      String feed,
      String metric,
      Number value
  )
  {
    this.createdTime = createdTime != null ? createdTime : DateTimes.nowUtc();
    this.serviceDims = serviceDims;
    this.userDims = userDims;
    this.feed = feed;
    this.metric = metric;
    this.value = value;
  }

  public DateTime getCreatedTime()
  {
    return createdTime;
  }

  @Override
  public String getFeed()
  {
    return feed;
  }

  public String getService()
  {
    return serviceDims.get("service");
  }

  public String getHost()
  {
    return serviceDims.get("host");
  }

  public Map<String, Object> getUserDims()
  {
    return ImmutableMap.copyOf(userDims);
  }

  public String getMetric()
  {
    return metric;
  }

  public Number getValue()
  {
    return value;
  }

  @Override
  @JsonValue
  public EventMap toMap()
  {
    return EventMap
        .builder()
        .put("feed", getFeed())
        .put("timestamp", createdTime.toString())
        .putAll(serviceDims)
        .put("metric", metric)
        .put("value", value)
        .putAll(
            Maps.filterEntries(
                userDims,
                input -> input.getKey() != null
            )
        )
        .build();
  }

  /**
   * Creates an immutable copy of this metric event. This is used only in tests.
   */
  public ServiceMetricEvent copy()
  {
    return new ServiceMetricEvent(createdTime, serviceDims, Map.copyOf(userDims), feed, metric, value);
  }

  /**
   * Builder for a {@link ServiceMetricEvent}. This builder can be used for
   * building only one event.
   */
  public static class Builder extends ServiceEventBuilder<ServiceMetricEvent>
  {
    private final Map<String, Object> userDims = new TreeMap<>();
    private String feed = "metrics";
    private String metric;
    private Number value;
    private DateTime createdTime;

    public Builder setFeed(String feed)
    {
      this.feed = feed;
      return this;
    }

    public Builder setDimension(String dim, String[] values)
    {
      userDims.put(dim, Arrays.asList(values));
      return this;
    }

    public Builder setDimensionIfNotNull(String dim, Object value)
    {
      if (value != null) {
        userDims.put(dim, value);
      }
      return this;
    }

    public Builder setDimension(String dim, Object value)
    {
      userDims.put(dim, value);
      return this;
    }

    public Object getDimension(String dim)
    {
      return userDims.get(dim);
    }

    public Builder setMetric(String metric, Number value)
    {
      if (Double.isNaN(value.doubleValue())) {
        throw new ISE("Value of NaN is not allowed!");
      }
      if (Double.isInfinite(value.doubleValue())) {
        throw new ISE("Value of Infinite is not allowed!");
      }

      this.metric = metric;
      this.value = value;
      return this;
    }

    public Builder setCreatedTime(DateTime createdTime)
    {
      this.createdTime = createdTime;
      return this;
    }

    @Override
    public ServiceMetricEvent build(ImmutableMap<String, String> serviceDimensions)
    {
      Preconditions.checkNotNull(metric, "Metric is not set");
      Preconditions.checkNotNull(value, "Value is not set");

      return new ServiceMetricEvent(
          createdTime,
          serviceDimensions,
          userDims,
          feed,
          metric,
          value
      );
    }
  }
}
