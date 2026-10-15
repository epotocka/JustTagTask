package com.example.justtagtask.service.loading;

import java.util.Set;
import java.util.UUID;

import com.example.justtagtask.model.upload.enums.FileStatus;
import com.example.justtagtask.repository.upload.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CanonicalLoadJobListener implements JobExecutionListener {

    private static final Set<FileStatus> LOADABLE = Set.of(FileStatus.READY, FileStatus.STOPPED, FileStatus.FAILED);
    private static final int MAX_REASON_LENGTH = 2500;
    private static final String DEFAULT_FAILURE_REASON = "The load failed";

    private final UploadedFileRepository uploadedFileRepository;

    @Override
    public void beforeJob(JobExecution execution) {
        uploadedFileRepository.transition(CanonicalLoadJobParameters.fileIdOf(execution), LOADABLE, FileStatus.LOADING);
    }

    @Override
    public void afterJob(JobExecution execution) {
        UUID fileId = CanonicalLoadJobParameters.fileIdOf(execution);
        switch (execution.getStatus()) {
            case COMPLETED -> uploadedFileRepository.updateStatus(fileId, FileStatus.LOADED);
            case STOPPING, STOPPED -> uploadedFileRepository.updateStatus(fileId, FileStatus.STOPPED);
            default -> uploadedFileRepository.markFailed(fileId, failureReason(execution));
        }
    }

    private static String failureReason(JobExecution execution) {
        String description = execution.getExitStatus().getExitDescription();
        return description.isBlank()
                ? DEFAULT_FAILURE_REASON
                : description.substring(0, Math.min(description.length(), MAX_REASON_LENGTH));
    }

}
