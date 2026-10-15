package com.example.justtagtask.service.common;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchJobs {

    private final JobRepository jobRepository;
    private final JobOperator jobOperator;

    public List<JobExecution> executions(String jobName, JobParameters identifyingParameters) {
        JobInstance instance = jobRepository.getJobInstance(jobName, identifyingParameters);
        if (instance == null) {
            return List.of();
        }
        return jobRepository.getJobExecutions(instance).stream()
                .sorted(Comparator.comparingLong(JobExecution::getId))
                .toList();
    }

    public Optional<JobExecution> lastExecution(String jobName, JobParameters identifyingParameters) {
        List<JobExecution> executions = executions(jobName, identifyingParameters);
        return executions.isEmpty() ? Optional.empty() : Optional.of(executions.getLast());
    }

    public List<JobExecution> recoverInterrupted(String jobName) {
        return jobRepository.findRunningJobExecutions(jobName).stream()
                .map(jobOperator::recover)
                .toList();
    }

}
