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
}
