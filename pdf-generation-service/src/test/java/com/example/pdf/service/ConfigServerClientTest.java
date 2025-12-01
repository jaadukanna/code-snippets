package com.example.pdf.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

class ConfigServerClientTest {

    @Test
    void getFile_parsesJsonConfigServerResponse() throws Exception {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer srv = MockRestServiceServer.createServer(rt);

        String path = "mappings/base-application.yml";
        String url = "http://localhost:8888/application/default/main/" + path;

        String body = "{\"propertySources\":[{\"name\":\"base\",\"source\":{\"mapping.pdf.field.issuedDate\":\"invoiceDate\"}}],\"version\":\"v1\"}";

        srv.expect(requestTo(url))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ConfigServerClient client = new ConfigServerClient(rt, "http://localhost:8888");
        ConfigServerClient.ConfigServerResponse resp = client.getFile("default", "main", path);

        Assertions.assertNotNull(resp);
        Assertions.assertNotNull(resp.propertySources);
        Assertions.assertEquals("base", resp.propertySources.get(0).name);
        Map<String, Object> src = resp.propertySources.get(0).source;
        Assertions.assertTrue(src.containsKey("mapping.pdf.field.issuedDate"));

        srv.verify();
    }

    @Test
    void getFile_parsesYamlFileContent() throws Exception {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer srv = MockRestServiceServer.createServer(rt);

        String path = "mappings/templates/invoice-v2.yml";
        String url = "http://localhost:8888/application/default/main/" + path;

        String yaml = "mapping:\n  pdf:\n    field:\n      invoiceNumber: payload.order.id\n";

        srv.expect(requestTo(url))
                .andRespond(withSuccess(yaml, MediaType.parseMediaType("application/x-yaml")));

        ConfigServerClient client = new ConfigServerClient(rt, "http://localhost:8888");
        ConfigServerClient.ConfigServerResponse resp = client.getFile("default", "main", path);

        Assertions.assertNotNull(resp);
        Assertions.assertNotNull(resp.propertySources);
        Map<String, Object> src = resp.propertySources.get(0).source;
        // YAML should be parsed into a nested map with top-level 'mapping'
        Assertions.assertTrue(src.containsKey("mapping"));
        Object mappingNode = src.get("mapping");
        Assertions.assertTrue(mappingNode instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String,Object> mappingMap = (Map<String,Object>) mappingNode;
        Assertions.assertTrue(mappingMap.containsKey("pdf"));

        srv.verify();
    }

    @Test
    void getFile_returnsNullOnNotFound() throws Exception {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer srv = MockRestServiceServer.createServer(rt);

        String path = "mappings/states/NOPE.yml";
        String url = "http://localhost:8888/application/default/main/" + path;

        srv.expect(requestTo(url)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        ConfigServerClient client = new ConfigServerClient(rt, "http://localhost:8888");
        ConfigServerClient.ConfigServerResponse resp = client.getFile("default", "main", path);

        Assertions.assertNull(resp);

        srv.verify();
    }

    @Test
    void getFile_handlesMalformedJsonAndYamlGracefully() throws Exception {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer srv = MockRestServiceServer.createServer(rt);

        String path = "mappings/bad.yml";
        String url = "http://localhost:8888/application/default/main/" + path;

        // Return invalid JSON
        srv.expect(requestTo(url))
                .andRespond(withSuccess("this is not json nor yaml", MediaType.APPLICATION_JSON));

        ConfigServerClient client = new ConfigServerClient(rt, "http://localhost:8888");
        ConfigServerClient.ConfigServerResponse resp = client.getFile("default", "main", path);

        // Should be null because parsing fails
        Assertions.assertNull(resp);
        srv.verify();
    }

    @Test
    void getFile_handlesEmptyBodyAsNull() throws Exception {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer srv = MockRestServiceServer.createServer(rt);

        String path = "mappings/empty.yml";
        String url = "http://localhost:8888/application/default/main/" + path;

        srv.expect(requestTo(url))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        ConfigServerClient client = new ConfigServerClient(rt, "http://localhost:8888");
        ConfigServerClient.ConfigServerResponse resp = client.getFile("default", "main", path);

        Assertions.assertNull(resp);
        srv.verify();
    }

    @Test
    void getFile_handlesPlainTextNonYamlAsNull() throws Exception {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer srv = MockRestServiceServer.createServer(rt);

        String path = "mappings/plain.txt";
        String url = "http://localhost:8888/application/default/main/" + path;

        srv.expect(requestTo(url))
                .andRespond(withSuccess("just some plain text that is not yaml", MediaType.TEXT_PLAIN));

        ConfigServerClient client = new ConfigServerClient(rt, "http://localhost:8888");
        ConfigServerClient.ConfigServerResponse resp = client.getFile("default", "main", path);

        Assertions.assertNull(resp);
        srv.verify();
    }
}
