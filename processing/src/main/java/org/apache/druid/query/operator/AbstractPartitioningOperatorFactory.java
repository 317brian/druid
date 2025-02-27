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

package org.apache.druid.query.operator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractPartitioningOperatorFactory implements OperatorFactory
{
  protected final List<String> partitionColumns;

  @JsonCreator
  public AbstractPartitioningOperatorFactory(
      @JsonProperty("partitionColumns") List<String> partitionColumns
  )
  {
    this.partitionColumns = partitionColumns == null ? new ArrayList<>() : partitionColumns;
  }

  @JsonProperty("partitionColumns")
  public List<String> getPartitionColumns()
  {
    return partitionColumns;
  }

  @Override
  public abstract Operator wrap(Operator op);

  @Override
  public boolean validateEquivalent(OperatorFactory other)
  {
    if (other instanceof AbstractPartitioningOperatorFactory) {
      return partitionColumns.equals(((AbstractPartitioningOperatorFactory) other).getPartitionColumns());
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(partitionColumns);
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    AbstractPartitioningOperatorFactory other = (AbstractPartitioningOperatorFactory) obj;
    return Objects.equals(partitionColumns, other.partitionColumns);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "{partitionColumns=" + partitionColumns + "}";
  }
}
