package com.couchbase.connector.elasticsearch.io.transformerchain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FilterTransformer extends TransformerChain {

    private final Set<String> filterSet;

    public FilterTransformer(TransformerConfig config) {
        super(config);
        this.filterSet = filterFieldSet(config.getFilterFields());
    }

    @Override
    public Map<String, Object> process(Map<String, Object> source) {
        return filter(source);
    }

    private Map<String, Object> filter(Map<String, Object> source) {
        final Map<String, Object> filtered = new HashMap<>();

        for(String filter : filterSet) {
            addFilteredFieldToDocument(filter, source, filtered);
        }

        return filtered;
    }

    private void addFilteredFieldToDocument(String filter, Map<String, Object> source, Map<String, Object> target) {
        if(filter.contains(".")) {
            String filterRoot = filter.substring(0, filter.indexOf("."));
            if(source.containsKey(filterRoot)){
                if(source.get(filterRoot) instanceof Map) {
                    if(!target.containsKey(filterRoot)){
                        target.put(filterRoot, new HashMap<>());
                    }
                    addFilteredFieldToDocument(filter.substring(filter.indexOf(".")+1), (Map<String, Object>) source.get(filterRoot), (Map<String, Object>) target.get(filterRoot));
                }
                else if(source.get(filterRoot) instanceof ArrayList) {
                    if(!target.containsKey(filterRoot)){
                        target.put(filterRoot, new ArrayList<>());
                    }

                    for(Object object : (ArrayList<Object>) source.get(filterRoot)) {
                        if(object instanceof Map) {
                            Map<String, Object> filteredSubObject = new HashMap<>();
                            addFilteredFieldToDocument(filter.substring(filter.indexOf(".")+1), (Map<String, Object>) object, filteredSubObject);
                            ((ArrayList<Object>)target.get(filterRoot)).add(filteredSubObject);
                        }
                    }
                }
            }
        }
        else {
            target.put(filter, source.get(filter));
        }
    }

    private Set<String> filterFieldSet(String filterFields) {
        Set<String> filterFieldSet = new HashSet<>();
        Arrays.stream(filterFields.split(",")).forEach(filter -> filterFieldSet.add(filter.trim()));
        return filterFieldSet;
    }
}
