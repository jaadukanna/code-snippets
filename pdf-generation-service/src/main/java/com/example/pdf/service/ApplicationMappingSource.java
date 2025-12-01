package com.example.pdf.service;

import com.example.pdf.controller.GenerateRequest;

import java.util.Map;
import java.util.Optional;

/**
 * Fetches configuration published under an application name from the config server.
 * Example application name: "order-service-invoice-v2".
 */
public class ApplicationMappingSource implements MappingSource {

    private final ConfigServerClient client;
    private final String applicationName;

    public ApplicationMappingSource(ConfigServerClient client, String applicationName) {
        this.client = client;
        this.applicationName = applicationName;
    }

    @Override
    public Optional<Map<String, Object>> fetch(GenerateRequest req, String label) throws Exception {
        ConfigServerClient.ConfigServerResponse resp = client.getApplicationConfig(applicationName, "default", label);
        if (resp == null || resp.propertySources == null || resp.propertySources.isEmpty()) return Optional.empty();
        Map<String, Object> source = resp.propertySources.get(0).source;
        if (source == null) return Optional.empty();
        return Optional.of(source);
    }
}
