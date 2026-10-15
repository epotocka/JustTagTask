package com.example.justtagtask.dto.upload;

import java.util.UUID;

import com.example.justtagtask.model.upload.enums.FileStatus;

public record UploadResponse(UUID fileId, FileStatus status) {
}
