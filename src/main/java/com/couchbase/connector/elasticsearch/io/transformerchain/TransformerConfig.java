package com.couchbase.connector.elasticsearch.io.transformerchain;

public class TransformerConfig {

    private String filterFields;
    private String denormalizeFields;
    private String excludeFields;

    public static Builder builder() {
        return new Builder();
    }

    public String getFilterFields() {
        return this.filterFields;
    }

    public String getDenormalizeFields() {
        return denormalizeFields;
    }

    public String getExcludeFields() {
        return excludeFields;
    }

    public static class Builder {
        private String filterFields;
        private String denormalizeFields;
        private String excludeFields;

        public Builder filterFields(String filterFields) {
            this.filterFields = filterFields;
            return this;
        }

        public Builder denormalizeFields(String denormalizeFields) {
            this.denormalizeFields = denormalizeFields;
            return this;
        }

        public Builder excludeFields(String excludeFields) {
            this.excludeFields = excludeFields;
            return this;
        }

        public TransformerConfig build() {
            TransformerConfig config = new TransformerConfig();
            config.filterFields = filterFields;
            config.denormalizeFields = denormalizeFields;
            config.excludeFields = excludeFields;
            return config;
        }
    }
}
