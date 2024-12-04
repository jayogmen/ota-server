package com.ota.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArtifactInfo {
    private String projectName;
    private String artifactId;
    private String version;
    private String url;
    private String updateType;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
    private Map<String, Object> esp32_metadata;
    private UpdateData data;

    public String getProjectName() { return projectName; }
    public String getVersion() { return version; }
    public String getUpdateType() { return updateType; }
    public String getUrl() { return url; }
    public Map<String, Object> getMetadata() { return metadata; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Map<String, Object> getEsp32Metadata() { return esp32_metadata; }

    public void setProjectName(String projectName) { this.projectName = projectName; }
    public void setVersion(String version) { this.version = version; }
    public void setUpdateType(String updateType) { this.updateType = updateType; }
    public void setUrl(String url) { this.url = url; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setEsp32Metadata(Map<String, Object> esp32_metadata) { this.esp32_metadata = esp32_metadata; }
}
