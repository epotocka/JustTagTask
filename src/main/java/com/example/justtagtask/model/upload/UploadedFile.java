package com.example.justtagtask.model.upload;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import com.example.justtagtask.model.upload.enums.FileStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("uploaded_file")
public record UploadedFile(
        @Id UUID id,
        String originalFilename,
        String storagePath,
        FileStatus status,
        String failureReason,
        long bytesReceived,
        long rowCount,
        Instant createdAt,
        Instant updatedAt) {

    public static UploadedFile uploading(UUID id, String originalFilename, Path storagePath) {
        Instant now = Instant.now();
        return new UploadedFile(id, originalFilename, storagePath.toString(), FileStatus.UPLOADING, null, 0, 0, now, now);
    }

}
