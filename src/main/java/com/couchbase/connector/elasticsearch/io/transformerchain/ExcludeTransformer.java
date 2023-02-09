package com.couchbase.connector.elasticsearch.io.transformerchain;

import joptsimple.internal.Strings;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ExcludeTransformer extends TransformerChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExcludeTransformer.class);

    private final Set<String> excludeFields;

    public ExcludeTransformer(TransformerConfig config) {
        super(config);
        excludeFields = getExcludeFields(config.getExcludeFields());
    }

    @Override
    protected Map<String, Object> process(Map<String, Object> source) {
        return exclude(source);
    }

    private Map<String, Object> exclude(Map<String, Object> source) {
        LOGGER.info("Exclude operation is starting with {}", Strings.join(excludeFields, ","));
        for (String excludeField : excludeFields) {
            removeExcludedFields(source, excludeField);
        }
        LOGGER.info("Exclude operation is finish with {}", Strings.join(excludeFields, ","));

        return source;
    }

    private void removeExcludedFields(Map<String, Object> source, String excludeField) {
        if (source == null || StringUtils.isEmpty(excludeField)) {
            return;
        }

        if (!excludeField.contains(".")) {
            source.remove(excludeField);
            return;
        }

        String prefixPart = excludeField.substring(0, excludeField.indexOf("."));
        String remainingPart = excludeField.replace(prefixPart + ".", "");

        if (!source.containsKey(prefixPart)) {
            return;
        }

        Object object = source.get(prefixPart);
        if (Objects.isNull(object)) {
            return;
        }

        if (object instanceof Collection) {

            for (Object subObject : (Collection) object) {
                if (subObject instanceof Map) {
                    removeExcludedFields((Map) subObject, remainingPart);
                }
            }
        } else if (object instanceof Map) {
            removeExcludedFields((Map) object, remainingPart);
        } else {
            LOGGER.warn("Unknown exclusion key! {}", excludeField);
        }

    }

    private Set<String> getExcludeFields(String excludeFields) {
        Set<String> excludeFieldsValue = new HashSet<>();
        Arrays.stream(excludeFields.split(",")).forEach(filter -> excludeFieldsValue.add(filter.trim()));
        return excludeFieldsValue;
    }
}
