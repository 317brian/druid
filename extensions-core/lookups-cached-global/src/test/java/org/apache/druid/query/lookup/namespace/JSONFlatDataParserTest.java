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

package org.apache.druid.query.lookup.namespace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import org.apache.druid.data.input.MapPopulator;
import org.apache.druid.jackson.DefaultObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSONFlatDataParserTest
{
  private static final ObjectMapper MAPPER = new DefaultObjectMapper();
  private static final String KEY1 = "foo1";
  private static final String KEY2 = "foo2";
  private static final String VAL1 = "bar";
  private static final String VAL2 = "baz";
  private static final String OTHERVAL1 = "3";
  private static final String OTHERVAL2 = null;
  private static final String CANBEEMPTY1 = "";
  private static final String CANBEEMPTY2 = "notEmpty";
  private static final List<Map<String, Object>> MAPPINGS = ImmutableList.of(
      ImmutableMap.of("key", "foo1", "val", "bar", "otherVal", 3, "canBeEmpty", ""),
      ImmutableMap.of("key", "foo2", "val", "baz", "canBeEmpty", "notEmpty")
  );
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  private File tmpFile;

  @Before
  public void setUp() throws Exception
  {
    tmpFile = temporaryFolder.newFile("lookup.json");
    final CharSink sink = Files.asByteSink(tmpFile).asCharSink(StandardCharsets.UTF_8);
    sink.writeLines(
        Iterables.transform(
            MAPPINGS,
            new Function<>()
            {
              @Override
              public CharSequence apply(Map<String, Object> input)
              {
                try {
                  return MAPPER.writeValueAsString(input);
                }
                catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }
            }
        )
    );
  }

  @Test
  public void testSimpleParse() throws Exception
  {
    final UriExtractionNamespace.JSONFlatDataParser parser = new UriExtractionNamespace.JSONFlatDataParser(
        MAPPER,
        "key",
        "val"
    );
    final Map<String, String> map = new HashMap<>();
    new MapPopulator<>(parser.getParser()).populate(Files.asByteSource(tmpFile), map);
    Assert.assertEquals(VAL1, map.get(KEY1));
    Assert.assertEquals(VAL2, map.get(KEY2));
  }

  @Test
  public void testSimpleParseWithByteLimit_limitNotReached() throws Exception
  {
    final UriExtractionNamespace.JSONFlatDataParser parser = new UriExtractionNamespace.JSONFlatDataParser(
        MAPPER,
        "key",
        "val"
    );
    final Map<String, String> map = new HashMap<>();
    new MapPopulator<>(parser.getParser()).populateAndWarnAtByteLimit(
        Files.asByteSource(tmpFile),
        map,
        100_000L,
        "namespace");
    Assert.assertEquals(VAL1, map.get(KEY1));
    Assert.assertEquals(VAL2, map.get(KEY2));
  }

  @Test
  public void testSimpleParseWithByteLimit_limitReached() throws Exception
  {
    final UriExtractionNamespace.JSONFlatDataParser parser = new UriExtractionNamespace.JSONFlatDataParser(
        MAPPER,
        "key",
        "val"
    );
    final Map<String, String> map = new HashMap<>();
    new MapPopulator<>(parser.getParser()).populateAndWarnAtByteLimit(
        Files.asByteSource(tmpFile),
        map,
        1L,
        "namespace");
    Assert.assertEquals(VAL1, map.get(KEY1));
    Assert.assertEquals(VAL2, map.get(KEY2));
  }

  @Test
  public void testParseWithNullValues() throws Exception
  {
    final UriExtractionNamespace.JSONFlatDataParser parser = new UriExtractionNamespace.JSONFlatDataParser(
        MAPPER,
        "key",
        "otherVal"
    );
    final Map<String, String> map = new HashMap<>();
    new MapPopulator<>(parser.getParser()).populate(Files.asByteSource(tmpFile), map);
    Assert.assertEquals(OTHERVAL1, map.get(KEY1));
    Assert.assertEquals(OTHERVAL2, map.get(KEY2));
  }

  @Test
  public void testParseWithEmptyValues() throws Exception
  {
    final UriExtractionNamespace.JSONFlatDataParser parser = new UriExtractionNamespace.JSONFlatDataParser(
        MAPPER,
        "key",
        "canBeEmpty"
    );
    final Map<String, String> map = new HashMap<>();
    new MapPopulator<>(parser.getParser()).populate(Files.asByteSource(tmpFile), map);
    Assert.assertEquals(CANBEEMPTY1, map.get(KEY1));
    Assert.assertEquals(CANBEEMPTY2, map.get(KEY2));
  }

  @Test
  public void testFailParseOnKeyMissing() throws Exception
  {
    final UriExtractionNamespace.JSONFlatDataParser parser = new UriExtractionNamespace.JSONFlatDataParser(
        MAPPER,
        "keyWHOOPS",
        "val"
    );
    final Map<String, String> map = new HashMap<>();

    expectedException.expect(NullPointerException.class);
    expectedException.expectMessage("Key column [keyWHOOPS] missing data in line");

    new MapPopulator<>(parser.getParser()).populate(Files.asByteSource(tmpFile), map);
  }
}
