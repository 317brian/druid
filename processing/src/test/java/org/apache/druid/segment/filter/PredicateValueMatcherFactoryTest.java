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

package org.apache.druid.segment.filter;

import com.google.common.collect.ImmutableList;
import org.apache.druid.collections.bitmap.RoaringBitmapFactory;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.query.filter.SelectorPredicateFactory;
import org.apache.druid.query.filter.ValueMatcher;
import org.apache.druid.segment.DimensionSelector;
import org.apache.druid.segment.SimpleAscendingOffset;
import org.apache.druid.segment.column.ColumnType;
import org.apache.druid.segment.data.GenericIndexed;
import org.apache.druid.segment.data.VSizeColumnarInts;
import org.apache.druid.segment.data.VSizeColumnarMultiInts;
import org.apache.druid.segment.selector.TestColumnValueSelector;
import org.apache.druid.segment.serde.StringUtf8DictionaryEncodedColumnSupplier;
import org.apache.druid.testing.InitializedNullHandlingTest;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class PredicateValueMatcherFactoryTest extends InitializedNullHandlingTest
{
  @Test
  public void testDefaultType()
  {
    Assert.assertEquals(ColumnType.UNKNOWN_COMPLEX, forSelector(null).defaultType());
  }

  @Test
  public void testDimensionProcessorSingleValuedDimensionMatchingValue()
  {
    final ValueMatcher matcher = forSelector("0").makeDimensionProcessor(DimensionSelector.constant("0"), false);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testDimensionProcessorSingleValuedDimensionNotMatchingValue()
  {
    final ValueMatcher matcher = forSelector("1").makeDimensionProcessor(DimensionSelector.constant("0"), false);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testDimensionProcessorMultiValuedDimensionMatchingValue()
  {
    // Emulate multi-valued dimension
    final StringUtf8DictionaryEncodedColumnSupplier<?> columnSupplier = new StringUtf8DictionaryEncodedColumnSupplier<>(
        GenericIndexed.fromIterable(
            ImmutableList.of(
                ByteBuffer.wrap(StringUtils.toUtf8("v1")),
                ByteBuffer.wrap(StringUtils.toUtf8("v2")),
                ByteBuffer.wrap(StringUtils.toUtf8("v3"))
            ),
            GenericIndexed.UTF8_STRATEGY
        )::singleThreaded,
        null,
        () -> VSizeColumnarMultiInts.fromIterable(ImmutableList.of(VSizeColumnarInts.fromArray(new int[]{1}))),
        new RoaringBitmapFactory()
    );
    final ValueMatcher matcher = forSelector("v2")
        .makeDimensionProcessor(columnSupplier.get().makeDimensionSelector(new SimpleAscendingOffset(1), null), true);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testDimensionProcessorMultiValuedDimensionNotMatchingValue()
  {
    // Emulate multi-valued dimension
    final StringUtf8DictionaryEncodedColumnSupplier<?> columnSupplier = new StringUtf8DictionaryEncodedColumnSupplier(
        GenericIndexed.fromIterable(
            ImmutableList.of(
                ByteBuffer.wrap(StringUtils.toUtf8("v1")),
                ByteBuffer.wrap(StringUtils.toUtf8("v2")),
                ByteBuffer.wrap(StringUtils.toUtf8("v3"))
            ),
            GenericIndexed.UTF8_STRATEGY
        )::singleThreaded,
        null,
        () -> VSizeColumnarMultiInts.fromIterable(ImmutableList.of(VSizeColumnarInts.fromArray(new int[]{1}))),
        new RoaringBitmapFactory()
    );
    final ValueMatcher matcher = forSelector("v3")
        .makeDimensionProcessor(columnSupplier.get().makeDimensionSelector(new SimpleAscendingOffset(1), null), true);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testFloatProcessorMatchingValue()
  {
    final TestColumnValueSelector<Float> columnValueSelector = TestColumnValueSelector.of(
        Float.class,
        ImmutableList.of(2.f)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("2.f").makeFloatProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testFloatProcessorNotMatchingValue()
  {
    final TestColumnValueSelector<Float> columnValueSelector = TestColumnValueSelector.of(
        Float.class,
        ImmutableList.of(2.f)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("5.f").makeFloatProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testDoubleProcessorMatchingValue()
  {
    final TestColumnValueSelector<Double> columnValueSelector = TestColumnValueSelector.of(
        Double.class,
        ImmutableList.of(2.)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("2.").makeDoubleProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testDoubleProcessorNotMatchingValue()
  {
    final TestColumnValueSelector<Double> columnValueSelector = TestColumnValueSelector.of(
        Double.class,
        ImmutableList.of(2.)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("5.").makeDoubleProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testNumberProcessorMatchingValue()
  {
    Double num = 2.;
    final TestColumnValueSelector<Number> columnValueSelector = TestColumnValueSelector.of(
            Number.class,
            ImmutableList.of(new Number() {
              @Override
              public int intValue()
              {
                return num.intValue();
              }

              @Override
              public long longValue()
              {
                return num.longValue();
              }

              @Override
              public float floatValue()
              {
                return num.floatValue();
              }

              @Override
              public double doubleValue()
              {
                return num;
              }
            })
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("2").makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testNumberProcessorNotMatchingValue()
  {
    Double num = 2.;
    final TestColumnValueSelector<Double> columnValueSelector = TestColumnValueSelector.of(
            Double.class,
            ImmutableList.of(new Number() {
              @Override
              public int intValue()
              {
                return num.intValue();
              }

              @Override
              public long longValue()
              {
                return num.longValue();
              }

              @Override
              public float floatValue()
              {
                return num.floatValue();
              }

              @Override
              public double doubleValue()
              {
                return num;
              }
            })
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("5").makeComplexProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testLongProcessorMatchingValue()
  {
    final TestColumnValueSelector<Long> columnValueSelector = TestColumnValueSelector.of(
        Long.class,
        ImmutableList.of(2L)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("2").makeLongProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testLongProcessorNotMatchingValue()
  {
    final TestColumnValueSelector<Long> columnValueSelector = TestColumnValueSelector.of(
        Long.class,
        ImmutableList.of(2L)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("5").makeLongProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorMatchingNull()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        Arrays.asList(null, "v")
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector(null).makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorEmptyString()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        Arrays.asList("", "v")
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector(null).makeComplexProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorMatchingInteger()
  {
    final TestColumnValueSelector<Integer> columnValueSelector = TestColumnValueSelector.of(
        Integer.class,
        ImmutableList.of(11)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("11").makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorNotMatchingInteger()
  {
    final TestColumnValueSelector<Integer> columnValueSelector = TestColumnValueSelector.of(
        Integer.class,
        ImmutableList.of(15)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("11").makeComplexProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorMatchingLong()
  {
    final TestColumnValueSelector<Long> columnValueSelector = TestColumnValueSelector.of(
        Long.class,
        ImmutableList.of(11L)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("11").makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorNotMatchingLong()
  {
    final TestColumnValueSelector<Long> columnValueSelector = TestColumnValueSelector.of(
        Long.class,
        ImmutableList.of(15L)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("11").makeComplexProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorMatchingFloat()
  {
    final TestColumnValueSelector<Float> columnValueSelector = TestColumnValueSelector.of(
        Float.class,
        ImmutableList.of(11.f)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("11.f").makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorNotMatchingFloat()
  {
    final TestColumnValueSelector<Float> columnValueSelector = TestColumnValueSelector.of(
        Float.class,
        ImmutableList.of(15.f)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("11.f").makeComplexProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorMatchingDouble()
  {
    final TestColumnValueSelector<Double> columnValueSelector = TestColumnValueSelector.of(
        Double.class,
        ImmutableList.of(11.d)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("11.d").makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorNotMatchingDouble()
  {
    final TestColumnValueSelector<Double> columnValueSelector = TestColumnValueSelector.of(
        Double.class,
        ImmutableList.of(15.d)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("11.d").makeComplexProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorMatchingString()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        ImmutableList.of("val")
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("val").makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorNotMatchingString()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        ImmutableList.of("bar")
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("val").makeComplexProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorMatchingStringList()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        ImmutableList.of(ImmutableList.of("val"))
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("val").makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorNotMatchingStringList()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        ImmutableList.of(ImmutableList.of("bar"))
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("val").makeComplexProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorMatchingEmptyList()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        ImmutableList.of(ImmutableList.of())
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector(null).makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorMatchingBoolean()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        ImmutableList.of(false)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("false").makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorNotMatchingBoolean()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        ImmutableList.of(true)
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("false").makeComplexProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorMatchingByteArray()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        ImmutableList.of(StringUtils.toUtf8("var"))
    );
    columnValueSelector.advance();
    final String base64Encoded = StringUtils.encodeBase64String(StringUtils.toUtf8("var"));
    final ValueMatcher matcher = forSelector(base64Encoded).makeComplexProcessor(columnValueSelector);
    Assert.assertTrue(matcher.matches(false));
  }

  @Test
  public void testComplexProcessorNotMatchingByteArray()
  {
    final TestColumnValueSelector<String> columnValueSelector = TestColumnValueSelector.of(
        String.class,
        ImmutableList.of(StringUtils.toUtf8("var"))
    );
    columnValueSelector.advance();
    final ValueMatcher matcher = forSelector("val").makeComplexProcessor(columnValueSelector);
    Assert.assertFalse(matcher.matches(false));
  }

  private static PredicateValueMatcherFactory forSelector(@Nullable String value)
  {
    return new PredicateValueMatcherFactory(new SelectorPredicateFactory(value));
  }
}
