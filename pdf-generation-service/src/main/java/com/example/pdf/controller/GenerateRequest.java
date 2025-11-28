package com.example.pdf.controller;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public class GenerateRequest {

    @NotBlank
    private String templateName;

    @NotBlank
    private String clientService;

    private Map<String, Object> payload;

    // optional YAML mapping override
    private String mappingOverride;

    private String label;

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getClientService() {
        return clientService;
    }

    public void setClientService(String clientService) {
        this.clientService = clientService;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getMappingOverride() {
        return mappingOverride;
    }

    public void setMappingOverride(String mappingOverride) {
        this.mappingOverride = mappingOverride;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
