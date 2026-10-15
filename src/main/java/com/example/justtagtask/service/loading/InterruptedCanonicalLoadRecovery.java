package com.example.justtagtask.service.loading;

import com.example.justtagtask.repository.upload.UploadedFileRepository;
import com.example.justtagtask.service.common.BatchJobs;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterruptedCanonicalLoadRecovery implements ApplicationRunner {

    private static final String REASON = "The server stopped during the load";

    private final BatchJobs batchJobs;
    private final UploadedFileRepository uploadedFileRepository;

    @Override
    public void run(ApplicationArguments args) {
        batchJobs.recoverInterrupted(CanonicalLoadJobParameters.JOB_NAME)
                .forEach(execution -> uploadedFileRepository.markFailed(CanonicalLoadJobParameters.fileIdOf(execution), REASON));
    }

}
