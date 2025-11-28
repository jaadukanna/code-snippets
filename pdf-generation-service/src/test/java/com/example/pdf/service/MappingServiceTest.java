package com.example.pdf.service;

import com.example.pdf.controller.GenerateRequest;
import com.example.pdf.model.MappingDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class MappingServiceTest {

    private final MappingService service = new MappingService();

    @Test
    void parseOverrideIntoStructuredDocument() throws Exception {
        String yaml = "template:\n  url: https://example.com/templates/invoice-v2.pdf\nmapping:\n  pdf:\n    field:\n      customerName: customer.name\n      invoiceNumber: invoiceId\n      totalAmount: total\nmetadata:\n  version: v2\n";

        GenerateRequest req = new GenerateRequest();
        req.setMappingOverride(yaml);

        MappingDocument doc = service.resolveMappingDocument(req);

        Assertions.assertNotNull(doc.getTemplate());
        Assertions.assertEquals("https://example.com/templates/invoice-v2.pdf", doc.getTemplate().getUrl());
        Assertions.assertNotNull(doc.getMapping());
        Assertions.assertNotNull(doc.getMapping().getPdf());
        Assertions.assertEquals("customer.name", doc.getMapping().getPdf().getField().get("customerName"));
    }

    @Test
    void resolvePathSimple() {
        Map<String, Object> payload = Map.of(
                "customer", Map.of("name", "Acme"),
                "invoiceId", "INV-1001",
                "total", 99.95
        );

        Object name = service.resolvePath(payload, "customer.name");
        Object total = service.resolvePath(payload, "total");
        Object missing = service.resolvePath(payload, "not.exists");

        Assertions.assertEquals("Acme", name);
        Assertions.assertEquals(99.95, total);
        Assertions.assertNull(missing);
    }
}
