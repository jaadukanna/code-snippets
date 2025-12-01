package com.example.pdf.service;

import com.example.pdf.controller.GenerateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compose mapping fragments by fetching candidate fragments (via mapping sources)
 * and deep-merging them in order.
 */
public class MappingComposer {

    private static final Logger log = LoggerFactory.getLogger(MappingComposer.class);

    private final ConfigServerClient client;

    public MappingComposer(ConfigServerClient client) {
        this.client = client;
    }

    /**
     * Compose using textual candidate names. If a candidate contains '/', it is
     * treated as a repo file path (ConfigFileMappingSource will be used); otherwise
     * it is treated as an application name (ApplicationMappingSource).
     */
    public Map<String, Object> compose(GenerateRequest req, String label, List<String> candidates) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (String candidate : candidates) {
            try {
                MappingSource src;
                if (candidate.contains("/")) {
                    String path = candidate + ".yml";
                    src = new ConfigFileMappingSource(client, path);
                } else {
                    src = new ApplicationMappingSource(client, candidate);
                }

                src.fetch(req, label).ifPresent(fragment -> {
                    Map<String, Object> nested = unflatten(fragment);
                    if (nested.containsKey("pdf") && !nested.containsKey("mapping")) {
                        Object pdfNode = nested.remove("pdf");
                        Map<String, Object> mappingNode = new LinkedHashMap<>();
                        mappingNode.put("pdf", pdfNode);
                        nested.put("mapping", mappingNode);
                    }
                    deepMerge(merged, nested);
                });
            } catch (Exception ex) {
                log.warn("Ignoring candidate {} due to error: {}", candidate, ex.toString());
            }
        }
        return merged;
    }

    // Unflatten a map with dotted keys into a nested map
    @SuppressWarnings("unchecked")
    private Map<String, Object> unflatten(Map<?,?> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Object ko : flat.keySet()) {
            String k = String.valueOf(ko);
            Object v = flat.get(ko);
            String[] parts = k.split("\\.");
            Map<String, Object> cur = root;
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                if (i == parts.length - 1) {
                    cur.put(p, v);
                } else {
                    Object next = cur.get(p);
                    if (!(next instanceof Map)) {
                        Map<String, Object> nm = new LinkedHashMap<>();
                        cur.put(p, nm);
                        cur = nm;
                    } else {
                        cur = (Map<String, Object>) next;
                    }
                }
            }
        }
        return root;
    }

    // Deep-merge override into base. For Map values, merge recursively; lists are replaced.
    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> base, Map<String, Object> override) {
        for (Map.Entry<String, Object> e : override.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v instanceof Map && base.get(k) instanceof Map) {
                deepMerge((Map<String, Object>) base.get(k), (Map<String, Object>) v);
            } else {
                base.put(k, v);
            }
        }
    }
}
