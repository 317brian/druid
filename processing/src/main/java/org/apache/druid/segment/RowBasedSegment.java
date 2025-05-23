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

package org.apache.druid.segment;

import com.google.common.base.Preconditions;
import org.apache.druid.java.util.common.Intervals;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.segment.column.RowSignature;
import org.apache.druid.timeline.SegmentId;
import org.joda.time.Interval;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A {@link Segment} that is based on a stream of objects.
 */
public class RowBasedSegment<RowType> implements Segment
{
  private final Sequence<RowType> rowSequence;
  private final RowAdapter<RowType> rowAdapter;
  private final RowSignature rowSignature;

  /**
   * Create a row-based segment.
   *
   * The provided "rowIterable" must be in time-order according to the provided {@link RowAdapter#timestampFunction()}.
   * The cursor returned by {@link RowBasedCursorFactory#makeCursorHolder(CursorBuildSpec)} makes no attempt to verify
   * this, and callers will expect it.
   *
   * The provided "rowSignature" will be used for reporting available columns and their capabilities to users of
   * {@link #as(Class)} to get a {@link CursorFactory}. Note that the {@link ColumnSelectorFactory} implementation
   * returned by this segment's cursor factory will allow creation of selectors on any field, using the
   * {@link RowAdapter#columnFunction} for that field, even if it doesn't appear in "rowSignature".
   *
   * @param rowSequence  objects that comprise this segment. Must be re-iterable if support for {@link Cursor#reset()}
   *                     is required. Otherwise, does not need to be re-iterable.
   * @param rowAdapter   adapter used for reading these objects
   * @param rowSignature signature of the columns in these objects
   */
  public RowBasedSegment(
      final Sequence<RowType> rowSequence,
      final RowAdapter<RowType> rowAdapter,
      final RowSignature rowSignature
  )
  {
    this.rowSignature = Preconditions.checkNotNull(rowSignature, "rowSignature");
    this.rowSequence = Preconditions.checkNotNull(rowSequence, "rowSequence");
    this.rowAdapter = Preconditions.checkNotNull(rowAdapter, "rowAdapter");
  }

  @Override
  public SegmentId getId()
  {
    return null;
  }

  @Override
  @Nonnull
  public Interval getDataInterval()
  {
    return Intervals.ETERNITY;
  }

  @Nullable
  @Override
  public <T> T as(@Nonnull Class<T> clazz)
  {
    if (CursorFactory.class.equals(clazz)) {
      return (T) new RowBasedCursorFactory<>(rowSequence, rowAdapter, rowSignature);
    }
    return null;
  }

  @Override
  public void close()
  {
    // Do nothing.
  }
}
