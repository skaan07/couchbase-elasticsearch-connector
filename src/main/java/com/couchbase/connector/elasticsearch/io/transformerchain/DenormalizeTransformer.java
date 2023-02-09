package com.couchbase.connector.elasticsearch.io.transformerchain;

import com.couchbase.connector.config.ConfigException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DenormalizeTransformer extends TransformerChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(DenormalizeTransformer.class);

    private final Map<String, String> denormalizeConfigs;

    public DenormalizeTransformer(TransformerConfig config) {
        super(config);
        denormalizeConfigs = getDenormalizeConfig(config.getDenormalizeFields());
    }

    @Override
    public Map<String, Object> process(Map<String, Object> source) {
        return denormalize(source);
    }

    private Map<String, Object> denormalize(Map<String, Object> filteredCBDocument) {
        Map<String, Object> result = new HashMap<>();
        recurseDenormalize(filteredCBDocument, result, result, "", false);

        return result;
    }

    private void recurseDenormalize(Map<String,Object> source, Map<String, Object> target, Map<String, Object> targetRoot, String path, boolean isList) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String currentPath = (StringUtils.isNotEmpty(path) ? path + "." : "") + entry.getKey();
            if (entry.getValue() instanceof Map) {
                Map<String, Object> subMap = new HashMap<>();
                recurseDenormalize((Map<String, Object>) entry.getValue(), subMap, targetRoot, currentPath, isList);
                if(subMap.size()>0) {
                    target.put(entry.getKey(), subMap);
                }
            } else if (entry.getValue() instanceof List) {
                List<Object> subList = new ArrayList<>();
                for (Object val : (List<Object>) entry.getValue()) {
                    if (val instanceof Map) {
                        Map<String, Object> subMap = new HashMap<>();
                        recurseDenormalize((Map<String, Object>) val, subMap, targetRoot, currentPath, true);
                        if(subMap.size()>0) {
                            subList.add(subMap);
                        }
                    } else {
                        subList.add(val);
                    }
                }

                if (denormalizeConfigs.containsKey(currentPath)) {
                    denormalizeTo(denormalizeConfigs.get(currentPath), targetRoot, subList, isList);
                }
                else if(subList.size()>0) {
                    target.put(entry.getKey(), subList);
                }
            } else {
                if (denormalizeConfigs.containsKey(currentPath)) {
                    denormalizeTo(denormalizeConfigs.get(currentPath), targetRoot, entry.getValue(), isList);
                } else {
                    target.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void denormalizeTo(String key, Map<String, Object> target, Object element, boolean isList) {
        if(isList) {
            List<Object> values;
            if(!target.containsKey(key)) {
                values = new ArrayList<>();
                target.put(key, values);
            }
            else {
                values = (List<Object>) target.get(key);
            }
            values.add(element);
        }
        else{
            target.put(key, element);
        }
    }

    private Map<String, String> getDenormalizeConfig(String denormalizeFields) {
        Map<String, String> result = new HashMap<>();

        if(StringUtils.isEmpty(denormalizeFields.trim())) {
            LOGGER.info("Denormalize Config Empty. Skip denormalize step.");
            return result;
        }

        String[] denormalizeConfigs = denormalizeFields.trim().split(",");
        for(String denormalizeConfig : denormalizeConfigs) {
            if(!denormalizeConfig.contains(":")) {
                LOGGER.error("Skipping denormalize config for " + denormalizeConfig);
                throw new ConfigException("Error in denormalizeConfig " + denormalizeConfig);
            }

            String[] denormalizeConfigSplit = denormalizeConfig.trim().split(":");

            if(denormalizeConfigSplit.length != 2) {
                LOGGER.error("Skipping denormalize config for " + denormalizeConfig);
                throw new ConfigException("Error in denormalizeConfig " + denormalizeConfig);
            }

            // Key : Source; Value : Target
            result.put(denormalizeConfigSplit[0].trim(), denormalizeConfigSplit[1].trim());
        }

        return result;
    }
}
