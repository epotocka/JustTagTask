package com.example.justtagtask.dto.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.justtagtask.dto.common.ExecutionView;
import com.example.justtagtask.dto.processing.ProcessingSummary;
import com.example.justtagtask.model.upload.UploadedFile;
import com.example.justtagtask.model.upload.enums.FileStatus;

public record FileDetails(
        UUID fileId,
        String originalFilename,
        FileStatus status,
        String failureReason,
        long bytesReceived,
        long rowCount,
        Instant createdAt,
        Instant updatedAt,
        ExecutionView load,
        List<ProcessingSummary> runs) {

    public static FileDetails of(UploadedFile file, ExecutionView load, List<ProcessingSummary> runs) {
        return new FileDetails(
                file.id(),
                file.originalFilename(),
                file.status(),
                file.failureReason(),
                file.bytesReceived(),
                file.rowCount(),
                file.createdAt(),
                file.updatedAt(),
                load,
                runs);
    }

}
