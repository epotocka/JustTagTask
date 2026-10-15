package com.example.justtagtask.service.processing;

import java.util.stream.Stream;

import com.example.justtagtask.model.processing.enums.RunStatus;
import com.example.justtagtask.repository.processing.ProcessingRunRepository;
import com.example.justtagtask.service.common.BatchJobs;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterruptedProcessingRecovery implements ApplicationRunner {

    private final BatchJobs batchJobs;
    private final ProcessingRunRepository processingRunRepository;

    @Override
    public void run(ApplicationArguments args) {
        Stream.of(ProcessingJobParameters.ROW_OPERATION_JOB, ProcessingJobParameters.AGGREGATION_JOB)
                .flatMap(jobName -> batchJobs.recoverInterrupted(jobName).stream())
                .forEach(execution -> processingRunRepository.updateStatus(ProcessingJobParameters.runIdOf(execution), RunStatus.FAILED));
    }

}
