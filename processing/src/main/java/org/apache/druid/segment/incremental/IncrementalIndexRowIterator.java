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

package org.apache.druid.segment.incremental;

import org.apache.druid.query.Order;
import org.apache.druid.segment.ColumnSelectorFactory;
import org.apache.druid.segment.ColumnValueSelector;
import org.apache.druid.segment.CursorBuildSpec;
import org.apache.druid.segment.DimensionHandler;
import org.apache.druid.segment.IndexableAdapter;
import org.apache.druid.segment.RowNumCounter;
import org.apache.druid.segment.RowPointer;
import org.apache.druid.segment.TimeAndDimsPointer;
import org.apache.druid.segment.TransformableRowIterator;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementation of {@link IndexableAdapter#getRows()} for {@link IncrementalIndexAdapter}.
 */
class IncrementalIndexRowIterator implements TransformableRowIterator
{
  private final Iterator<IncrementalIndexRow> timeAndDimsIterator;

  private final IncrementalIndexRowHolder currentRowHolder = new IncrementalIndexRowHolder();
  private final IncrementalIndexRowHolder markedRowHolder = new IncrementalIndexRowHolder();
  /** Initially -1 so that after the first call to {@link #moveToNext()} the row number is 0. */
  private final RowNumCounter currentRowNumCounter = new RowNumCounter(-1);
  private final RowPointer currentRowPointer;
  private final TimeAndDimsPointer markedRowPointer;

  IncrementalIndexRowIterator(IncrementalIndexRowSelector incrementalIndex)
  {
    this.timeAndDimsIterator = incrementalIndex.getFacts().persistIterable().iterator();
    this.currentRowPointer = makeRowPointer(incrementalIndex, currentRowHolder, currentRowNumCounter);
    // markedRowPointer doesn't actually need to be a RowPointer (just a TimeAndDimsPointer), but we create a RowPointer
    // in order to reuse the makeRowPointer() method. Passing a dummy RowNumCounter.
    this.markedRowPointer = makeRowPointer(incrementalIndex, markedRowHolder, new RowNumCounter());
  }

  private static RowPointer makeRowPointer(
      IncrementalIndexRowSelector incrementalIndex,
      IncrementalIndexRowHolder rowHolder,
      RowNumCounter rowNumCounter
  )
  {
    ColumnSelectorFactory columnSelectorFactory =
        new IncrementalIndexColumnSelectorFactory(
            incrementalIndex,
            rowHolder,
            CursorBuildSpec.FULL_SCAN,
            incrementalIndex.getTimePosition() == 0 ? Order.ASCENDING : Order.NONE
        );
    ColumnValueSelector[] dimensionSelectors = incrementalIndex
        .getDimensions()
        .stream()
        .map(dim -> {
          ColumnValueSelector selectorWithUnsortedValues = columnSelectorFactory.makeColumnValueSelector(dim.getName());
          return dim.getIndexer().convertUnsortedValuesToSorted(selectorWithUnsortedValues);
        })
        .toArray(ColumnValueSelector[]::new);
    List<DimensionHandler> dimensionHandlers = incrementalIndex
        .getDimensions()
        .stream()
        .map(IncrementalIndex.DimensionDesc::getHandler)
        .collect(Collectors.toList());
    ColumnValueSelector[] metricSelectors = incrementalIndex
        .getMetricNames()
        .stream()
        .map(columnSelectorFactory::makeColumnValueSelector)
        .toArray(ColumnValueSelector[]::new);

    return new RowPointer(
        rowHolder,
        incrementalIndex.getTimePosition(),
        dimensionSelectors,
        dimensionHandlers,
        metricSelectors,
        incrementalIndex.getMetricNames(),
        rowNumCounter
    );
  }

  @Override
  public boolean moveToNext()
  {
    if (!timeAndDimsIterator.hasNext()) {
      // Do NOT change currentRowHolder, to conform to RowIterator.getPointer() specification.
      return false;
    }
    currentRowHolder.set(timeAndDimsIterator.next());
    currentRowNumCounter.increment();
    return true;
  }

  @Override
  public RowPointer getPointer()
  {
    return currentRowPointer;
  }

  @Override
  public void mark()
  {
    markedRowHolder.set(currentRowHolder.get());
  }

  @Override
  public TimeAndDimsPointer getMarkedPointer()
  {
    return markedRowPointer;
  }

  @Override
  public boolean hasTimeAndDimsChangedSinceMark()
  {
    return !Objects.equals(markedRowHolder.get(), currentRowHolder.get());
  }

  @Override
  public void close()
  {
    // do nothing
  }
}
