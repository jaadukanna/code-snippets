package com.example.pdf.service;

import com.example.pdf.controller.GenerateRequest;

import java.util.Map;
import java.util.Optional;

/**
 * Fetches a mapping file from the config server repo via the file endpoint.
 * The `path` should be the repo-relative path (e.g. "mappings/base-application.yml").
 */
public class ConfigFileMappingSource implements MappingSource {

    private final ConfigServerClient client;
    private final String path;

    public ConfigFileMappingSource(ConfigServerClient client, String path) {
        this.client = client;
        this.path = path;
    }

    @Override
    public Optional<Map<String, Object>> fetch(GenerateRequest req, String label) throws Exception {
        ConfigServerClient.ConfigServerResponse resp = client.getFile("default", label, path);
        if (resp == null || resp.propertySources == null || resp.propertySources.isEmpty()) return Optional.empty();
        Map<String, Object> source = resp.propertySources.get(0).source;
        if (source == null) return Optional.empty();
        return Optional.of(source);
    }
}
