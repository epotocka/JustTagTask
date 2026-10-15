package com.example.justtagtask.service.processing;

import com.example.justtagtask.model.processing.enums.RunStatus;
import com.example.justtagtask.repository.processing.ProcessingRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProcessingJobListener implements JobExecutionListener {

    private final ProcessingRunRepository processingRunRepository;

    @Override
    public void beforeJob(JobExecution execution) {
        processingRunRepository.updateStatus(ProcessingJobParameters.runIdOf(execution), RunStatus.STARTED);
    }

    @Override
    public void afterJob(JobExecution execution) {
        RunStatus status = switch (execution.getStatus()) {
            case COMPLETED -> RunStatus.COMPLETED;
            case STOPPING, STOPPED -> RunStatus.STOPPED;
            default -> RunStatus.FAILED;
        };
        processingRunRepository.updateStatus(ProcessingJobParameters.runIdOf(execution), status);
    }

}
