package com.example.justtagtask.dto.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.justtagtask.dto.processing.ProcessingSummary;
import com.example.justtagtask.model.upload.UploadedFile;
import com.example.justtagtask.model.upload.enums.FileStatus;

public record FileSummary(
        UUID fileId,
        String originalFilename,
        FileStatus status,
        String failureReason,
        long rowCount,
        Instant createdAt,
        List<ProcessingSummary> runs) {

    public static FileSummary of(UploadedFile file, List<ProcessingSummary> runs) {
        return new FileSummary(
                file.id(),
                file.originalFilename(),
                file.status(),
                file.failureReason(),
                file.rowCount(),
                file.createdAt(),
                runs);
    }

}
