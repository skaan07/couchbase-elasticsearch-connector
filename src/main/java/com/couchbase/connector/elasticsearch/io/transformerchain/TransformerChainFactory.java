package com.couchbase.connector.elasticsearch.io.transformerchain;

import org.apache.commons.lang3.StringUtils;

public class TransformerChainFactory {

    public static TransformerChain getTransformerChain(TransformerConfig config){
        TransformerChain chain = new EmptyTransformer(config);
        if(StringUtils.isNotEmpty(config.getFilterFields())){
            chain.injectTransformer(new FilterTransformer(config));
        }
        if(StringUtils.isNotEmpty(config.getDenormalizeFields())){
            chain.injectTransformer(new DenormalizeTransformer(config));
        }
        if(StringUtils.isNotEmpty(config.getExcludeFields())){
            chain.injectTransformer(new ExcludeTransformer(config));
        }

        return chain;
    }
}
