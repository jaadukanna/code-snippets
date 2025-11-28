package com.example.pdf.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MappingService {

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper json = new ObjectMapper();

    // Resolve mapping either from override YAML or from Config Server
    public Map<String, String> resolveMapping(com.example.pdf.controller.GenerateRequest req) throws Exception {
        System.out.println("Resolving mapping for clientService='" + req.getClientService() +
                "', templateName='" + req.getTemplateName() +
                "', label='" + req.getLabel() + "'");
        if (StringUtils.hasText(req.getMappingOverride())) {
            System.out.println("Using mapping override YAML:\n" + req.getMappingOverride());
            // parse YAML override into a simple map
            Map<?,?> parsed = yaml.readValue(req.getMappingOverride(), Map.class);
            return flattenToStringMap(parsed);
        }

        String label = StringUtils.hasText(req.getLabel()) ? req.getLabel() : "main";
        System.out.println("Fetching mapping from Config Server with label='" + label + "'");   
        // mapping name convention: {clientService}-{templateName}
        String mappingName = req.getClientService() + "-" + req.getTemplateName();
        System.out.println("Mapping name: " + mappingName);
        String url = String.format("http://localhost:8888/%s/default/%s", mappingName, label);
        System.out.println("Config Server URL: " + url);
        ResponseEntity<Map> resp = rest.getForEntity(url, Map.class);
        Map body = resp.getBody();
        System.out.println("Config Server response body: " + yaml.writeValueAsString(body));    
        if (body == null) {
            System.out.println("Empty response body from Config Server");   
            return Map.of();
        }

        List propertySources = (List) body.get("propertySources");
        if (propertySources == null || propertySources.isEmpty()) return Map.of();
        Map first = (Map) propertySources.get(0);
        Map source = (Map) first.get("source");
        if (source == null) return Map.of();
        // source may be flattened like {"pdf.field.customerName":"payload.customer.name"}
        Map<String, String> result = new LinkedHashMap<>();
        for (Object k : source.keySet()) {
            Object v = source.get(k);
            result.put(String.valueOf(k), v == null ? "" : String.valueOf(v));
        }
        return result;
    }
    
    public com.example.pdf.model.MappingDocument resolveMappingDocument(com.example.pdf.controller.GenerateRequest req) throws Exception {
        // If inline override YAML is provided, parse into a Map then reuse unflatten+wrap logic
        if (StringUtils.hasText(req.getMappingOverride())) {
            // parse YAML override into a Map (may contain dotted keys)
            Map<?,?> parsed = yaml.readValue(req.getMappingOverride(), Map.class);
            Map<String, Object> nested = unflatten(parsed);
            System.out.println("Unflattened inline mapping override:\n" + yaml.writeValueAsString(nested));
            if (nested.containsKey("pdf") && !nested.containsKey("mapping")) {
                Object pdfNode = nested.remove("pdf");
                Map<String, Object> mappingNode = new LinkedHashMap<>();
                mappingNode.put("pdf", pdfNode);
                nested.put("mapping", mappingNode);
                System.out.println("Wrapped root 'pdf' under 'mapping' for inline override\n" + yaml.writeValueAsString(nested));
            }
            return json.convertValue(nested, com.example.pdf.model.MappingDocument.class);
        }
        
        String label = StringUtils.hasText(req.getLabel()) ? req.getLabel() : "main";
        System.out.println("Fetching mapping document from Config Server with label='" + label + "'");
        // mapping name convention: {clientService}-{templateName}
        String mappingName = req.getClientService() + "-" + req.getTemplateName();
        System.out.println("Mapping name: " + mappingName);
        String url = String.format("http://localhost:8888/%s/default/%s", mappingName, label);
        System.out.println("Config Server URL: " + url);
        ResponseEntity<Map> resp = rest.getForEntity(url, Map.class);
        Map body = resp.getBody();
        if (body == null) {
            System.out.println("Empty response body from Config Server");
            return new com.example.pdf.model.MappingDocument();
        }
        
        List propertySources = (List) body.get("propertySources");
        if (propertySources == null || propertySources.isEmpty()) {
            System.out.println("No propertySources found in Config Server response");   
            return new com.example.pdf.model.MappingDocument();
        }

        Map first = (Map) propertySources.get(0);
        Map source = (Map) first.get("source");
        if (source == null) {
            System.out.println("No source found in first propertySource");
            return new com.example.pdf.model.MappingDocument();
        }
        // source may be flattened (dotted keys). Unflatten into nested map first
        Map<String, Object> nested = unflatten(source);
        System.out.println("Unflattened mapping document:\n" + yaml.writeValueAsString(nested));

        // Some mappings are stored as flattened keys starting with `pdf.field...` which
        // unflatten into a root `pdf` node. Our typed model expects `mapping.pdf`.
        // If we see `pdf` at the root, but no `mapping` node, wrap it under `mapping`.
        if (nested.containsKey("pdf") && !nested.containsKey("mapping")) {
            Object pdfNode = nested.remove("pdf");
            Map<String, Object> mappingNode = new LinkedHashMap<>();
            mappingNode.put("pdf", pdfNode);
            nested.put("mapping", mappingNode);
            System.out.println("Wrapped root 'pdf' under 'mapping' for compatibility\n" + yaml.writeValueAsString(nested));
        }

        // convert nested map into MappingDocument
        return json.convertValue(nested, com.example.pdf.model.MappingDocument.class);
    }

    // Resolve a dotted path into the payload map
    public Object resolvePath(Map<String, Object> payload, String path) {
        System.out.println("resolvePath:Resolving path '" + path + "' in payload");
        if (path == null) return null;
        String[] parts = path.split("\\.");
        Object cur = payload;
        for (String p : parts) {
            if (!(cur instanceof Map)) return null;
            Map m = (Map) cur;
            cur = m.get(p);
        }
        System.out.println("resolvePath: Resolved value: " + (cur == null ? "null" : cur.toString()));
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
