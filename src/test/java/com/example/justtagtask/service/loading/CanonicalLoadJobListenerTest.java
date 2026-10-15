package com.example.justtagtask.service.loading;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import com.example.justtagtask.repository.upload.UploadedFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;

class CanonicalLoadJobListenerTest {

    private static final UUID FILE_ID = UUID.randomUUID();

    private final UploadedFileRepository uploadedFileRepository = mock(UploadedFileRepository.class);
    private final CanonicalLoadJobListener canonicalLoadJobListener = new CanonicalLoadJobListener(uploadedFileRepository);

    @Test
    void givesAFailedLoadWithoutAnExitDescriptionADefaultReason() {
        JobExecution execution = failedExecution(ExitStatus.FAILED);

        canonicalLoadJobListener.afterJob(execution);

        verify(uploadedFileRepository).markFailed(FILE_ID, "The load failed");
    }

    @Test
    void truncatesALongExitDescription() {
        JobExecution execution = failedExecution(ExitStatus.FAILED.addExitDescription("x".repeat(3_000)));

        canonicalLoadJobListener.afterJob(execution);

        verify(uploadedFileRepository).markFailed(FILE_ID, "x".repeat(2_500));
    }

    private static JobExecution failedExecution(ExitStatus exitStatus) {
        JobExecution execution = new JobExecution(
                1L, new JobInstance(1L, CanonicalLoadJobParameters.JOB_NAME), CanonicalLoadJobParameters.forFile(FILE_ID));
        execution.setStatus(BatchStatus.FAILED);
        execution.setExitStatus(exitStatus);
        return execution;
    }

}
