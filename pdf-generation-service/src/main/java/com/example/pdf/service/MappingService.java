package com.example.pdf.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MappingService {

    private static final Logger log = LoggerFactory.getLogger(MappingService.class);

    private final ConfigServerClient configClient;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper json = new ObjectMapper();

    public MappingService() {
        this.configClient = new ConfigServerClient(null, "http://localhost:8888");
    }

    // Constructor for tests or custom RestTemplate
    public MappingService(org.springframework.web.client.RestTemplate rest) {
        this.configClient = new ConfigServerClient(rest, "http://localhost:8888");
    }

    // Constructor to inject custom client
    public MappingService(ConfigServerClient client) {
        this.configClient = client == null ? new ConfigServerClient(null, "http://localhost:8888") : client;
    }

    // Resolve mapping either from override YAML or from Config Server
    public Map<String, String> resolveMapping(com.example.pdf.controller.GenerateRequest req) throws Exception {
        log.debug("Resolving mapping for clientService='{}', templateName='{}', label='{}'", req.getClientService(), req.getTemplateName(), req.getLabel());
        if (StringUtils.hasText(req.getMappingOverride())) {
            log.debug("Using mapping override YAML:\n{}", req.getMappingOverride());
            Map<?,?> parsed = yaml.readValue(req.getMappingOverride(), Map.class);
            return flattenToStringMap(parsed);
        }

        String label = StringUtils.hasText(req.getLabel()) ? req.getLabel() : "main";
        log.debug("Fetching mapping from Config Server with label='{}'", label);
        String mappingName = req.getClientService() + "-" + req.getTemplateName();
        log.debug("Mapping name: {}", mappingName);
        ConfigServerClient.ConfigServerResponse resp = configClient.getApplicationConfig(mappingName, "default", label);
        if (resp == null) {
            log.debug("Config server returned null for application {}", mappingName);
            return Map.of();
        }
        List<ConfigServerClient.PropertySource> propertySources = resp.propertySources;
        if (propertySources == null || propertySources.isEmpty()) return Map.of();
        Map<String, Object> source = propertySources.get(0).source;
        if (source == null) return Map.of();

        Map<String, String> result = new LinkedHashMap<>();
        for (Object k : source.keySet()) {
            Object v = source.get(k);
            result.put(String.valueOf(k), v == null ? "" : String.valueOf(v));
        }
        return result;
    }
    
    public com.example.pdf.model.MappingDocument resolveMappingDocument(com.example.pdf.controller.GenerateRequest req) throws Exception {
        if (StringUtils.hasText(req.getMappingOverride())) {
            Map<?,?> parsed = yaml.readValue(req.getMappingOverride(), Map.class);
            Map<String, Object> nested = unflatten(parsed);
            log.debug("Unflattened inline mapping override:\n{}", yaml.writeValueAsString(nested));
            if (nested.containsKey("pdf") && !nested.containsKey("mapping")) {
                Object pdfNode = nested.remove("pdf");
                Map<String, Object> mappingNode = new LinkedHashMap<>();
                mappingNode.put("pdf", pdfNode);
                nested.put("mapping", mappingNode);
                log.debug("Wrapped root 'pdf' under 'mapping' for inline override\n{}", yaml.writeValueAsString(nested));
            }
            return json.convertValue(nested, com.example.pdf.model.MappingDocument.class);
        }
        
        String label = StringUtils.hasText(req.getLabel()) ? req.getLabel() : "main";
        log.debug("Fetching mapping document from Config Server with label='{}'", label);
        String mappingName = req.getClientService() + "-" + req.getTemplateName();
        log.debug("Mapping name: {}", mappingName);
        ConfigServerClient.ConfigServerResponse resp = configClient.getApplicationConfig(mappingName, "default", label);
        if (resp == null) {
            log.debug("Empty response body from Config Server");
            return new com.example.pdf.model.MappingDocument();
        }

        List<ConfigServerClient.PropertySource> propertySources = resp.propertySources;
        if (propertySources == null || propertySources.isEmpty()) {
            log.debug("No propertySources found in Config Server response");
            return new com.example.pdf.model.MappingDocument();
        }

        Map<String, Object> source = propertySources.get(0).source;
        if (source == null) {
            log.debug("No source found in first propertySource");
            return new com.example.pdf.model.MappingDocument();
        }

        Map<String, Object> nested = unflatten(source);
        log.debug("Unflattened mapping document:\n{}", yaml.writeValueAsString(nested));

        if (nested.containsKey("pdf") && !nested.containsKey("mapping")) {
            Object pdfNode = nested.remove("pdf");
            Map<String, Object> mappingNode = new LinkedHashMap<>();
            mappingNode.put("pdf", pdfNode);
            nested.put("mapping", mappingNode);
            log.debug("Wrapped root 'pdf' under 'mapping' for compatibility\n{}", yaml.writeValueAsString(nested));
        }

        return json.convertValue(nested, com.example.pdf.model.MappingDocument.class);
    }

    /**
     * Compose mapping documents from multiple candidate sources based on the supplied attributes
     * (productType, marketCategory, state, templateName). The order is from least-specific
     * to most-specific; later maps override earlier ones.
     */
    public com.example.pdf.model.MappingDocument composeMappingDocument(com.example.pdf.controller.GenerateRequest req) throws Exception {
        if (StringUtils.hasText(req.getMappingOverride())) {
            return resolveMappingDocument(req);
        }

        String label = StringUtils.hasText(req.getLabel()) ? req.getLabel() : "main";

        String template = req.getTemplateName();
        String product = req.getProductType();
        String market = req.getMarketCategory();
        String state = req.getState();

        List<String> candidates = List.of(
                "mappings/base-application",
                String.format("mappings/templates/%s", template),
                String.format("mappings/products/%s", product),
                String.format("mappings/markets/%s", market),
                String.format("mappings/states/%s", state),
                String.format("mappings/templates/%s/%s", product, template)
        );

        MappingComposer composer = new MappingComposer(configClient);
        Map<String, Object> merged = composer.compose(req, label, candidates);
        return json.convertValue(merged, com.example.pdf.model.MappingDocument.class);
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

    // Resolve a dotted path into the payload map
    public Object resolvePath(Map<String, Object> payload, String path) {
        log.debug("resolvePath:Resolving path '{}' in payload", path);
        if (path == null) return null;
        String[] parts = path.split("\\.");
        Object cur = payload;
        for (String p : parts) {
            if (!(cur instanceof Map)) return null;
            Map m = (Map) cur;
            cur = m.get(p);
        }
        log.debug("resolvePath: Resolved value: {}", (cur == null ? "null" : cur.toString()));
        return cur;
    }

    // flatten nested YAML/Map into flat string->string map by joining keys with '.'
    private Map<String, String> flattenToStringMap(Map<?,?> input) {
        Map<String, String> out = new LinkedHashMap<>();
        flatten("", input, out);
        return out;
    }

    private void flatten(String prefix, Map<?,?> m, Map<String, String> out) {
        for (Object k : m.keySet()) {
            String key = prefix.isEmpty() ? String.valueOf(k) : prefix + "." + String.valueOf(k);
            Object v = m.get(k);
            if (v instanceof Map) {
                flatten(key, (Map<?,?>) v, out);
            } else {
                out.put(key, v == null ? "" : String.valueOf(v));
            }
        }
    }
    
    // Unflatten a map with dotted keys into a nested map
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
    
    // convenience: extract field mapping (pdf.field.*) as flat map of pdfField->payloadPath
    public Map<String, String> extractFieldMap(com.example.pdf.model.MappingDocument doc) {
        if (doc == null || doc.getMapping() == null || doc.getMapping().getPdf() == null) return Map.of();
        Map<String, String> fields = doc.getMapping().getPdf().getField();
        if (fields == null) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            out.put(e.getKey(), sanitizePath(e.getValue()));
        }
        return out;
    }

    // Strip common prefixes so mapping paths resolve relative to the payload map
    private String sanitizePath(String p) {
        if (p == null) return null;
        p = p.trim();
        if (p.startsWith("payload.")) return p.substring("payload.".length());
        if (p.startsWith("$.")) return p.substring(2);
        return p;
    }
}
