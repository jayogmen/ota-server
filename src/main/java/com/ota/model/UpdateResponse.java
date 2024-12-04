// UpdateResponse.java
package com.ota.model;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class UpdateResponse {
    private boolean status;
    private String message;
    private String updateType;
    private UpdateData data;

    public UpdateResponse(boolean status, String message) {
        this.status = status;
        this.message = message;
        this.updateType = null;
        this.data = null;
    }

    public UpdateResponse(boolean status, String message, UpdateData data) {
        this.status = status;
        this.message = message;
        this.updateType = "esp32-update";
        this.data = data;
    }
}


