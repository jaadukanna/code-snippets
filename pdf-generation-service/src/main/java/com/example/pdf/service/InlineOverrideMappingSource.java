package com.example.pdf.service;

import com.example.pdf.controller.GenerateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.Map;
import java.util.Optional;

/**
 * MappingSource that returns the inline `mappingOverride` from the request if present.
 */
public class InlineOverrideMappingSource implements MappingSource {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Override
    public Optional<Map<String, Object>> fetch(GenerateRequest req, String label) throws Exception {
        if (req == null || req.getMappingOverride() == null || req.getMappingOverride().isBlank()) return Optional.empty();
        Map<?,?> parsed = yaml.readValue(req.getMappingOverride(), Map.class);
        // upcast to Map<String,Object>
        return Optional.of((Map<String, Object>) parsed);
    }
}
