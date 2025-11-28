package com.example.pdf.model;

import java.util.Map;

public class MappingDocument {

    private Template template;
    private Mapping mapping;
    private Map<String, Object> metadata;

    public Template getTemplate() {
        return template;
    }

    public void setTemplate(Template template) {
        this.template = template;
    }

    public Mapping getMapping() {
        return mapping;
    }

    public void setMapping(Mapping mapping) {
        this.mapping = mapping;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public static class Template {
        private String url;
        private String type;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class Mapping {
        private Pdf pdf;

        public Pdf getPdf() { return pdf; }
        public void setPdf(Pdf pdf) { this.pdf = pdf; }

        public static class Pdf {
            private Map<String, String> field;

            public Map<String, String> getField() { return field; }
            public void setField(Map<String, String> field) { this.field = field; }
        }
    }
}
