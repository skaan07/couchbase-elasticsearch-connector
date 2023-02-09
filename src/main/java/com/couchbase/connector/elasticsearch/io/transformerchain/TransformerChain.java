package com.couchbase.connector.elasticsearch.io.transformerchain;

import java.util.Map;

public abstract class TransformerChain {

    protected final TransformerConfig config;
    private TransformerChain nextTransformer;

    public TransformerChain(TransformerConfig config) {
        this.config = config;
    }

    public void injectTransformer(TransformerChain next) {
        TransformerChain last = this;
        while(last.nextTransformer != null) {
            last = last.nextTransformer;
        }
        last.nextTransformer = next;
    }

    public Map<String, Object> transform(Map<String, Object> source) {
        Map<String, Object> result = this.process(source);

        if(this.nextTransformer != null) {
            result = this.nextTransformer.transform(result);
        }

        return result;
    }

    protected abstract Map<String, Object> process(Map<String, Object> source);
}
