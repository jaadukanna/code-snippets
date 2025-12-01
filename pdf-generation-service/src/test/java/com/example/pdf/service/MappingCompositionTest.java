package com.example.pdf.service;

import com.example.pdf.controller.GenerateRequest;
import com.example.pdf.model.MappingDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MappingCompositionTest {

    @Test
    void composeAndMergeCandidates() throws Exception {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer srv = MockRestServiceServer.createServer(rt);
        MappingService svc = new MappingService(rt);

        // Candidate: base-application (served as file under mappings/)
        srv.expect(requestTo("http://localhost:8888/application/default/main/mappings/base-application.yml"))
                .andRespond(withSuccess("{\"propertySources\":[{\"name\":\"base\",\"source\":{\"mapping.pdf.field.issuedDate\":\"invoiceDate\"}}],\"version\":\"v1\"}", MediaType.APPLICATION_JSON));

        // Candidate: templates/invoice-v2 (file under mappings/templates/)
        srv.expect(requestTo("http://localhost:8888/application/default/main/mappings/templates/invoice-v2.yml"))
                .andRespond(withSuccess("{\"propertySources\":[{\"name\":\"template\",\"source\":{\"mapping.pdf.field.invoiceNumber\":\"invoiceId\",\"mapping.pdf.field.customerName\":\"customer.name\"}}],\"version\":\"v1\"}", MediaType.APPLICATION_JSON));

        // Candidate: products/medicare (file under mappings/products/)
        srv.expect(requestTo("http://localhost:8888/application/default/main/mappings/products/medicare.yml"))
                .andRespond(withSuccess("{\"propertySources\":[{\"name\":\"product\",\"source\":{\"mapping.pdf.field.planName\":\"product.planName\"}}],\"version\":\"v1\"}", MediaType.APPLICATION_JSON));

        // others return 404 (no expectation)
        // stub market and state requests (file endpoints) with empty responses so they are not treated as unexpected
        srv.expect(requestTo("http://localhost:8888/application/default/main/mappings/markets/group.yml"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        srv.expect(requestTo("http://localhost:8888/application/default/main/mappings/states/CA.yml"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        // stub product-template override (file endpoint)
        srv.expect(requestTo("http://localhost:8888/application/default/main/mappings/templates/medicare/invoice-v2.yml"))
                .andRespond(withSuccess("{\"propertySources\":[{\"name\":\"product-template\",\"source\":{\"mapping.pdf.field.medicareSpecificField\":\"product.medicareField\"}}],\"version\":\"v1\"}", MediaType.APPLICATION_JSON));

        GenerateRequest req = new GenerateRequest();
        req.setTemplateName("invoice-v2");
        req.setProductType("medicare");
        req.setMarketCategory("group");
        req.setState("CA");
        req.setLabel("main");

        MappingDocument doc = svc.composeMappingDocument(req);

        Assertions.assertNotNull(doc.getMapping());
        Assertions.assertNotNull(doc.getMapping().getPdf());
        Map<String, String> fields = doc.getMapping().getPdf().getField();
        Assertions.assertTrue(fields.containsKey("issuedDate"));
        Assertions.assertTrue(fields.containsKey("invoiceNumber"));
        Assertions.assertTrue(fields.containsKey("planName"));

        srv.verify();
    }
}
