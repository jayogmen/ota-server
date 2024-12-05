package com.ota.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.model.ArtifactInfo;
import com.ota.model.UpdateData;
import com.ota.model.UpdateResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Slf4j
@Service
@RequiredArgsConstructor
public class OTAService {
    private static final String FULL_IMAGE_UPDATE_DIR = "/opt/ota-server/full-image-updates/";
    private static final String COMPONENT_UPDATE_DIR = "/opt/ota-server/component-updates/";
    private static final String ARTIFACTS_FILE = "/opt/ota-server/artifacts.json";
    private static final String DIAGNOSTIC_STATUS_DIR = "/opt/ota-server/diagnostic-status/";
    private final ObjectMapper objectMapper;
    
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9.-]+))?(?:\\+([a-zA-Z0-9.-]+))?$");

    public void saveArtifact(ArtifactInfo artifactInfo) {
        try {
            Map<String, List<ArtifactInfo>> artifacts = loadArtifacts();
            artifactInfo.setTimestamp(LocalDateTime.now());
            
            String normalizedProjectName = normalizeProjectName(artifactInfo.getProjectName());
            artifactInfo.setProjectName(normalizedProjectName);
            
            List<ArtifactInfo> projectArtifacts = artifacts.computeIfAbsent(
                normalizedProjectName, 
                k -> new ArrayList<>()
            );
            
            if (!isValidVersion(artifactInfo.getVersion())) {
                throw new IllegalArgumentException("Invalid version format. Must follow semantic versioning (e.g., 1.2.3)");
            }
            
            // Transfer metadata from data object if not set directly
            if (artifactInfo.getMetadata() == null && artifactInfo.getData() != null 
                && artifactInfo.getData().getMetadata() != null) {
                artifactInfo.setMetadata(artifactInfo.getData().getMetadata());
            }
            
            if ("component-update".equals(artifactInfo.getUpdateType())) {
                validateComponentUpdate(artifactInfo);
                saveComponentUpdate(artifactInfo);
            } else if ("full-image-update".equals(artifactInfo.getUpdateType())) {
                saveFullImageUpdate(artifactInfo);
            } else {
                throw new IllegalArgumentException("Invalid update type: " + artifactInfo.getUpdateType());
            }
            
            projectArtifacts.add(artifactInfo);
            projectArtifacts.sort(Comparator.comparing(ArtifactInfo::getTimestamp).reversed());
            
            saveArtifacts(artifacts);
            log.info("Saved new artifact: {}", artifactInfo);
            
        } catch (Exception e) {
            log.error("Failed to save artifact", e);
            throw new RuntimeException("Failed to save artifact: " + e.getMessage(), e);
        }
    }

    public UpdateResponse checkForUpdate(String deviceId, String projectName, String currentVersion, String updateType) {
        try {
            log.info("Checking for update - Device: {}, Project: {}, Current Version: {}, Update Type: {}", 
                     deviceId, projectName, currentVersion, updateType);
            
            String normalizedProjectName = normalizeProjectName(projectName);
            
            Map<String, List<ArtifactInfo>> artifacts = loadArtifacts();
            List<ArtifactInfo> projectArtifacts = artifacts.get(normalizedProjectName);
            
            if (projectArtifacts == null || projectArtifacts.isEmpty()) {
                log.info("No artifacts found for project: {}", normalizedProjectName);
                return new UpdateResponse(false, "No updates available", null, null);
            }
            
            log.info("Found {} artifacts for project {}", projectArtifacts.size(), normalizedProjectName);
            
            Optional<ArtifactInfo> latestCompatibleUpdate = projectArtifacts.stream()
                .filter(artifact -> artifact.getUpdateType().equalsIgnoreCase(updateType))
                .filter(artifact -> isNewerVersion(currentVersion, artifact.getVersion()))
                .findFirst();
            
            if (latestCompatibleUpdate.isPresent()) {
                ArtifactInfo latest = latestCompatibleUpdate.get();
                log.info("Found newer update - Current: {}, Latest: {}", 
                         currentVersion, latest.getVersion());
                
                UpdateData updateData = new UpdateData();
                updateData.setLatestSHA(latest.getVersion());
                updateData.setArtifactUrl(latest.getUrl());
                updateData.setUpdateType(latest.getUpdateType());
                updateData.setMetadata(latest.getMetadata());
                updateData.setVersion(latest.getVersion());
                updateData.setEsp32Update(latest.getEsp32Update());
                
                return new UpdateResponse(true, "Update available", latest.getUpdateType(), updateData);
            } else {
                log.info("No newer version available for current version {}", currentVersion);
                return new UpdateResponse(false, "Device is up to date", null, null);
            }
            
        } catch (Exception e) {
            log.error("Failed to check for updates", e);
            throw new RuntimeException("Failed to check for updates: " + e.getMessage(), e);
        }
    }

    private void validateComponentUpdate(ArtifactInfo artifactInfo) {
        if (artifactInfo.getMetadata() == null) {
            throw new IllegalArgumentException("Component update requires metadata");
        }
        
        Map<String, Object> metadata = artifactInfo.getMetadata();
        if (!metadata.containsKey("buildTime") ||
            !metadata.containsKey("gitCommit") ||
            !metadata.containsKey("branch") ||
            !metadata.containsKey("workflow") ||
            !metadata.containsKey("version")) {
            throw new IllegalArgumentException(
                "Component update metadata must include buildTime, gitCommit, branch, workflow, and version"
            );
        }
    }

    private void validateFullImageUpdate(ArtifactInfo artifactInfo) {
        if (artifactInfo.getUrl() == null || artifactInfo.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Full image update URL is required");
        }
        
        // Remove any docker:// prefix if present
        String url = artifactInfo.getUrl().replace("docker://", "");
        
        // Validate docker image URL format (organization/image_name)
        if (!url.matches("^[a-z0-9]+(?:[._-][a-z0-9]+)*/[a-z0-9]+(?:[._-][a-z0-9]+)*(?::[a-z0-9]+(?:[._-][a-z0-9]+)*)?$")) {
            throw new IllegalArgumentException(
                "Invalid Docker image URL format. Must be in format: organization/image_name[:tag]"
            );
        }
        
        // Update the URL without the docker:// prefix
        artifactInfo.setUrl(url);
    }

    private boolean isValidVersion(String version) {
        if (version == null) return false;
        return VERSION_PATTERN.matcher(version).matches();
    }

    private String normalizeProjectName(String projectName) {
        if (projectName == null) return "";
        return projectName.toLowerCase()
                         .replaceAll("-", "_")
                         .replaceAll("[^a-z0-9_]", "");
    }

    private boolean isNewerVersion(String currentVersion, String newVersion) {
        if (currentVersion == null || newVersion == null) {
            return false;
        }

        log.debug("Comparing versions - Current: {}, New: {}", currentVersion, newVersion);
        
        if (currentVersion.equals(newVersion)) {
            return false;
        }
        
        try {
            String[] current = currentVersion.split("\\.");
            String[] newer = newVersion.split("\\.");
            
            int[] currentParts = new int[3];
            int[] newerParts = new int[3];
            
            for (int i = 0; i < Math.min(current.length, 3); i++) {
                currentParts[i] = Integer.parseInt(current[i]);
            }
            
            for (int i = 0; i < Math.min(newer.length, 3); i++) {
                newerParts[i] = Integer.parseInt(newer[i]);
            }
            
            if (newerParts[0] != currentParts[0]) {
                return newerParts[0] > currentParts[0];
            }
            
            if (newerParts[1] != currentParts[1]) {
                return newerParts[1] > currentParts[1];
            }
            
            return newerParts[2] > currentParts[2];
            
        } catch (Exception e) {
            log.error("Error comparing versions: {} and {}", currentVersion, newVersion, e);
            return false;
        }
    }

    private void saveComponentUpdate(ArtifactInfo artifactInfo) {
        try {
            String filename = artifactInfo.getProjectName() + "-" + artifactInfo.getVersion() + ".json";
            File file = new File(COMPONENT_UPDATE_DIR + filename);
            Files.createDirectories(Paths.get(COMPONENT_UPDATE_DIR));
            objectMapper.writeValue(file, artifactInfo);
        } catch (Exception e) {
            log.error("Failed to save component update", e);
            throw new RuntimeException("Failed to save component update: " + e.getMessage(), e);
        }
    }

    private void saveFullImageUpdate(ArtifactInfo artifactInfo) {
        try {
            String filename = artifactInfo.getProjectName() + "-" + artifactInfo.getVersion() + ".json";
            File file = new File(FULL_IMAGE_UPDATE_DIR + filename);
            Files.createDirectories(Paths.get(FULL_IMAGE_UPDATE_DIR));
            objectMapper.writeValue(file, artifactInfo);
        } catch (Exception e) {
            log.error("Failed to save full image update", e);
            throw new RuntimeException("Failed to save full image update: " + e.getMessage(), e);
        }
    }

    private Map<String, List<ArtifactInfo>> loadArtifacts() throws Exception {
        File file = new File(ARTIFACTS_FILE);
        if (!file.exists()) {
            return new HashMap<>();
        }
    
        try {
            // Create JavaType for String
            JavaType stringType = objectMapper.getTypeFactory().constructType(String.class);
            
            // Create JavaType for List<ArtifactInfo>
            JavaType artifactListType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, ArtifactInfo.class);
            
            // Create JavaType for Map<String, List<ArtifactInfo>>
            JavaType mapType = objectMapper.getTypeFactory()
                .constructMapType(Map.class, stringType, artifactListType);
            
            return objectMapper.readValue(file, mapType);
        } catch (Exception e) {
            log.error("Error loading artifacts file: {}", e.getMessage());
            return new HashMap<>();
        }
    }


    private void saveArtifacts(Map<String, List<ArtifactInfo>> artifacts) throws Exception {
        File file = new File(ARTIFACTS_FILE);
        Files.createDirectories(file.getParentFile().toPath());
        objectMapper.writeValue(file, artifacts);
    }

    public void updateBuildStatus(String deviceId, String projectName, String version, Map<String, Object> buildStatus) {
        try {
            String normalizedProjectName = normalizeProjectName(projectName);
            String filename = String.format("%s/build_status_%s_%s_%s.json", 
                COMPONENT_UPDATE_DIR, normalizedProjectName, deviceId, version);
            
            File file = new File(filename);
            Files.createDirectories(Paths.get(COMPONENT_UPDATE_DIR));
            objectMapper.writeValue(file, buildStatus);
            
            log.info("Saved build status for device {} project {} version {}: {}", 
                deviceId, projectName, version, buildStatus);
        } catch (Exception e) {
            log.error("Failed to save build status", e);
            throw new RuntimeException("Failed to save build status: " + e.getMessage(), e);
        }
    }

    public void updateFullImageStatus(String deviceId, String projectName, String version, Map<String, Object> updateStatus) {
        try {
            String normalizedProjectName = normalizeProjectName(projectName);
            String filename = String.format("%s/image_status_%s_%s_%s.json", 
                FULL_IMAGE_UPDATE_DIR, normalizedProjectName, deviceId, version);
            
            File file = new File(filename);
            Files.createDirectories(Paths.get(FULL_IMAGE_UPDATE_DIR));
            objectMapper.writeValue(file, updateStatus);
            
            log.info("Saved full image update status for device {} project {} version {}: {}", 
                deviceId, projectName, version, updateStatus);
        } catch (Exception e) {
            log.error("Failed to save full image update status", e);
            throw new RuntimeException("Failed to save full image update status: " + e.getMessage(), e);
        }
    }

    public void updateDiagnosticStatus(String deviceId, String projectName, String version, Map<String, Object> diagnosticStatus) {
        try {
            String normalizedProjectName = normalizeProjectName(projectName);
            String filename = String.format("%s/diagnostic_status_%s_%s_%s.json", 
                DIAGNOSTIC_STATUS_DIR, normalizedProjectName, deviceId, version);
            
            File file = new File(filename);
            Files.createDirectories(Paths.get(DIAGNOSTIC_STATUS_DIR));
            objectMapper.writeValue(file, diagnosticStatus);
            
            log.info("Saved diagnostic status for device {} project {} version {}: {}", 
                deviceId, projectName, version, diagnosticStatus);
        } catch (Exception e) {
            log.error("Failed to save diagnostic status", e);
            throw new RuntimeException("Failed to save diagnostic status: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getDiagnosticStatus(String deviceId, String projectName, String version) {
        try {
            String normalizedProjectName = normalizeProjectName(projectName);
            String filename = String.format("%s/diagnostic_status_%s_%s_%s.json", 
                DIAGNOSTIC_STATUS_DIR, normalizedProjectName, deviceId, version);
            
            File file = new File(filename);
            if (!file.exists()) {
                return null;
            }
            
            return objectMapper.readValue(file, Map.class);
        } catch (Exception e) {
            log.error("Failed to read diagnostic status", e);
            return null;
        }
    }
}
