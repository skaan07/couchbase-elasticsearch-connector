/*
 * Copyright 2018 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.connector.elasticsearch.io;

import com.couchbase.client.dcp.core.utils.DefaultObjectMapper;
import com.couchbase.client.dcp.highlevel.Mutation;
import com.couchbase.connector.config.es.DocStructureConfig;
import com.couchbase.connector.dcp.Event;
import com.couchbase.connector.elasticsearch.io.transformerchain.TransformerChain;
import com.couchbase.connector.elasticsearch.io.transformerchain.TransformerChainFactory;
import com.couchbase.connector.elasticsearch.io.transformerchain.TransformerConfig;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static com.couchbase.client.core.logging.RedactableArgument.redactUser;

public class DefaultDocumentTransformer implements DocumentTransformer {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDocumentTransformer.class);

  private static final JsonFactory jsonFactory = new JsonFactory();

  private final boolean documentContentAtTopLevel;
  private final String metadataFieldName;
  private final boolean wrapCounters;

  private final TransformerChain transformerChain;

  public DefaultDocumentTransformer(DocStructureConfig docStructureConfig) {
    this.documentContentAtTopLevel = docStructureConfig.documentContentAtTopLevel();
    this.metadataFieldName = docStructureConfig.metadataFieldName();
    this.wrapCounters = docStructureConfig.wrapCounters();
    this.transformerChain = TransformerChainFactory.getTransformerChain(
        TransformerConfig.builder()
            .filterFields(docStructureConfig.filterFields())
            .denormalizeFields(docStructureConfig.denormalizeFields())
            .excludeFields(docStructureConfig.excludeFields())
            .build()
    );
  }

  private static boolean isSingleValidJsonObject(byte[] json) {
    try {
      final JsonParser parser = jsonFactory.createParser(json);

      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return false;
      }

      int depth = 1;
      JsonToken token;
      while ((token = parser.nextToken()) != null) {
        if (token == JsonToken.START_OBJECT) {
          depth++;
        } else if (token == JsonToken.END_OBJECT) {
          depth--;
          if (depth == 0 && parser.nextToken() != null) {
            // multiple JSON roots, or trailing garbage
            return false;
          }
        }
      }
    } catch (IOException e) {
      // malformed
      return false;
    }

    return true;
  }

  @Override
  public @Nullable Object getElasticsearchDocument(Event mutationEvent) {
    if (!mutationEvent.isMutation()) {
      throw new IllegalArgumentException("expected a mutation event");
    }

    final byte[] bytes = mutationEvent.getContent();

    // optimized pass-through
    if (documentContentAtTopLevel && metadataFieldName == null) {
      // Need to ensure valid JSON, otherwise bulk request fails with IOException.
      // That would be catastrophic, since we retry those.
      // Also, the doc root might be a counter which needs wrapping.
      if (isSingleValidJsonObject(bytes)) {
        return new PreserializedJson(bytes);
      }
    }

    final Map<String, Object> couchbaseDocument = getDocumentAsMap(bytes);
    if (couchbaseDocument == null) {
      LOGGER.debug("Skipping document {} because it's not a JSON Object", mutationEvent);
      return null;
    }

    Map<String, Object> transformedDocument = transformerChain.transform(couchbaseDocument);

    final Map<String, Object> esDocument;
    if (documentContentAtTopLevel) {
      esDocument = transformedDocument;
    } else {
      esDocument = new HashMap<>();
      esDocument.put("doc", transformedDocument);
    }

    if (metadataFieldName != null) {
      Mutation mutation = (Mutation) mutationEvent.getChange();
      if (esDocument.putIfAbsent(metadataFieldName, getMetadata(mutation)) != null) {
        LOGGER.warn("Metadata field name conflict; document {} already has field named '{}'",
            redactUser(mutationEvent), metadataFieldName);
      }
    }

    return esDocument;
  }

  @Nullable
  private Map<String, Object> getDocumentAsMap(byte[] bytes) {
    try {
      return DefaultObjectMapper.readValueAsMap(bytes);
    } catch (IOException notJsonObject) {
      return wrapCounters ? wrapIfCounter(bytes) : null;
    }
  }

  @Nullable
  private static Map<String, Object> wrapIfCounter(byte[] bytes) {
    final Long counter = getCounterValue(bytes);
    if (counter == null) {
      return null;
    }

    final Map<String, Object> result = new HashMap<>();
    result.put("value", counter);
    return result;
  }

  /**
   * If the given bytes are a valid JSON document whose root is an integral
   * number from 0 to 2^64-1 inclusive, returns the signed long representation
   * of the number. Otherwise returns null.
   */
  @Nullable
  static Long getCounterValue(byte[] bytes) {
    try {
      final JsonParser parser = jsonFactory.createParser(bytes);
      if (parser.nextValue() != JsonToken.VALUE_NUMBER_INT) {
        // root is not integral number
        return null;
      }

      // intentionally fail with ArithmeticException if it's outside the counter range (unsigned 64-bit int)
      final long counter = unsignedLongValueExact(parser.getBigIntegerValue());

      if (parser.nextValue() != null) {
        // not JSON -- garbage after root
        return null;
      }

      return counter;

    } catch (Exception notCounter) {
      // Not a counter (not JSON, or numeric value is outside of counter range).
      return null;
    }
  }

  private Map<String, Object> getMetadata(final Mutation mutation) {
    final long rev = mutation.getRevision();
    final long cas = mutation.getCas();
    final int expiration = mutation.getExpiry();
    final int flags = mutation.getFlagsAsInt();

    final Map<String, Object> meta = new HashMap<>();

    // Legacy CAPI metadata
    meta.put("rev", formatRevision(rev, cas, expiration, flags));
    meta.put("flags", flags);
    meta.put("expiration", expiration);
    meta.put("id", mutation.getKey());

    // Additional DCP metadata
    meta.put("vbucket", mutation.getVbucket());
    meta.put("vbuuid", mutation.getOffset().getVbuuid());
    meta.put("seqno", mutation.getOffset().getSeqno());
    meta.put("revSeqno", rev);
    meta.put("cas", cas);
    meta.put("lockTime", mutation.getLockTime());

    return meta;
  }

  /**
   * Returns serialized revision info in the form of revSeq-Cas+Expiration+Flags.
   * See https://github.com/couchbase/goxdcr/blob/b12a3057883f4898870d4da63faf2cfb36ba9ce7/parts/capi_nozzle.go#L1169
   */
  private static String formatRevision(long rev, long cas, int expiration, int flags) {
    // Sure, this could be a one-liner with String.format()... but that method is *terribly* slow.
    final String revString = Long.toUnsignedString(rev);
    final StringBuilder result = new StringBuilder(33 + revString.length())
        .append(revString).append("-");
    appendPaddedHexLong(result, cas);
    appendPaddedHexInt(result, expiration);
    appendPaddedHexInt(result, flags);
    return result.toString();
  }

  private static void appendPaddedHexLong(StringBuilder sb, long value) {
    final String hex = Long.toHexString(value);
    final int pad = (Long.BYTES * 2) - hex.length();
    appendZeroes(sb, pad).append(hex);
  }

  private static void appendPaddedHexInt(StringBuilder sb, int value) {
    final String hex = Integer.toHexString(value);
    final int pad = (Integer.BYTES * 2) - hex.length();
    appendZeroes(sb, pad).append(hex);
  }

  private static StringBuilder appendZeroes(StringBuilder sb, int count) {
    for (int i = 0; i < count; i++) {
      sb.append('0');
    }
    return sb;
  }

  /**
   * Unsigned counterpart to {@link BigInteger#longValueExact}.
   */
  private static long unsignedLongValueExact(BigInteger i) {
    try {
      return Long.parseUnsignedLong(i.toString());
    } catch (NumberFormatException e) {
      throw new ArithmeticException("BigInteger out of unsigned long range");
    }
  }
}
