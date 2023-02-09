package com.couchbase.connector.elasticsearch.io.transformerchain;

import java.util.Map;

public class EmptyTransformer extends TransformerChain {
    public EmptyTransformer(TransformerConfig config) {
        super(config);
    }

    @Override
    public Map<String, Object> process(Map<String, Object> source) {
        return source;
    }
}
