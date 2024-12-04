package com.ota.model;

import lombok.Data;
import java.util.Map;

@Data
public class UpdateData {
    private String latestSHA;
    private String artifactUrl;
    private String updateType;
    private Map<String, Object> metadata;
    private Map<String, Object> esp32_metadata;
    private String version;

    public void setEsp32Metadata(Map<String, Object> esp32_metadata) {
        this.esp32_metadata = esp32_metadata;
    }
}
