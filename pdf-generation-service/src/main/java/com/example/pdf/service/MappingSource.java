package com.example.pdf.service;

import com.example.pdf.controller.GenerateRequest;

import java.util.Map;
import java.util.Optional;

/**
 * Pluggable source of mapping fragments. Implementations fetch a fragment
 * (nested Map) for a given request and label.
 */
public interface MappingSource {
    Optional<Map<String, Object>> fetch(GenerateRequest req, String label) throws Exception;
}
