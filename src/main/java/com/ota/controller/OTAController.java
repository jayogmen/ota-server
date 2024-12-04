// OTAController.java
package com.ota.controller;

import com.ota.model.ArtifactInfo;
import com.ota.model.UpdateResponse;
import com.ota.service.OTAService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OTAController {
    private final OTAService otaService;
    
    @PostMapping("/saveArtifact")
    public ResponseEntity<UpdateResponse> saveArtifact(@RequestBody Map<String, Object> payload) {
        try {
            // Extract data from payload
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data == null) {
                return ResponseEntity.badRequest().body(
                    new UpdateResponse(false, "Missing data object", null, null)
                );
            }

            // Create ArtifactInfo from payload
            ArtifactInfo artifactInfo = new ArtifactInfo();
            artifactInfo.setVersion((String) data.get("latestSHA"));
            artifactInfo.setUrl((String) data.get("artifactUrl"));
            artifactInfo.setUpdateType((String) data.get("updateType"));
            artifactInfo.setProjectName("default-project");
            
            // Set metadata
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
            artifactInfo.setMetadata(metadata);
            
            artifactInfo.setEsp32Update((Boolean) data.get("esp32Update"));
            
            otaService.saveArtifact(artifactInfo);
            
            return ResponseEntity.ok(
                new UpdateResponse(true, "Artifact saved successfully", 
                    artifactInfo.getUpdateType(), null)
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                new UpdateResponse(false, e.getMessage(), null, null)
            );
        }
    }

    @GetMapping("/checkForUpdate/{deviceId}/{projectName}/{currentVersion}/{updateType}")
    public ResponseEntity<UpdateResponse> checkForUpdate(
            @PathVariable String deviceId,
            @PathVariable String projectName,
            @PathVariable String currentVersion,
            @PathVariable String updateType) {
        try {
            UpdateResponse updateResponse = otaService.checkForUpdate(deviceId, projectName, currentVersion, updateType);
            return ResponseEntity.ok(updateResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                new UpdateResponse(false, e.getMessage(), null, null)
            );
        }
    }

    @PostMapping("/updateBuildStatus/{deviceId}/{projectName}/{version}")
    public ResponseEntity<UpdateResponse> updateBuildStatus(
            @PathVariable String deviceId,
            @PathVariable String projectName,
            @PathVariable String version,
            @RequestBody Map<String, Object> buildStatus) {
        try {
            otaService.updateBuildStatus(deviceId, projectName, version, buildStatus);
            return ResponseEntity.ok(
                new UpdateResponse(true, "Build status updated successfully", null, null)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                new UpdateResponse(false, e.getMessage(), null, null)
            );
        }
    }

    @PostMapping("/updateFullImageStatus/{deviceId}/{projectName}/{version}")
    public ResponseEntity<UpdateResponse> updateFullImageStatus(
            @PathVariable String deviceId,
            @PathVariable String projectName,
            @PathVariable String version,
            @RequestBody Map<String, Object> updateStatus) {
        try {
            otaService.updateFullImageStatus(deviceId, projectName, version, updateStatus);
            return ResponseEntity.ok(
                new UpdateResponse(true, "Full image status updated successfully", "full-image-update", null)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                new UpdateResponse(false, e.getMessage(), null, null)
            );
        }
    }

    @PostMapping("/updateDiagnosticStatus/{deviceId}/{projectName}/{version}")
    public ResponseEntity<UpdateResponse> updateDiagnosticStatus(
            @PathVariable String deviceId,
            @PathVariable String projectName,
            @PathVariable String version,
            @RequestBody Map<String, Object> diagnosticStatus) {
        try {
            otaService.updateDiagnosticStatus(deviceId, projectName, version, diagnosticStatus);
            return ResponseEntity.ok(
                new UpdateResponse(true, "Diagnostic status updated successfully", "diagnostic-check", null)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                new UpdateResponse(false, e.getMessage(), null, null)
            );
        }
    }

    @GetMapping("/getDiagnosticStatus/{deviceId}/{projectName}/{version}")
    public ResponseEntity<Map<String, Object>> getDiagnosticStatus(
            @PathVariable String deviceId,
            @PathVariable String projectName,
            @PathVariable String version) {
        Map<String, Object> status = otaService.getDiagnosticStatus(deviceId, projectName, version);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
