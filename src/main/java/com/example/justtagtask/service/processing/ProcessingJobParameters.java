package com.example.justtagtask.service.processing;

import java.util.UUID;

import com.example.justtagtask.model.processing.enums.RunType;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

public final class ProcessingJobParameters {

    public static final String ROW_OPERATION_JOB = "rowOperationJob";
    public static final String AGGREGATION_JOB = "aggregationJob";
    public static final String ROW_OPERATION_STEP = "rowOperationStep";
    public static final String AGGREGATION_STEP = "aggregationStep";
    public static final String RUN_ID = "runId";

    private ProcessingJobParameters() {
    }

    public static JobParameters forRun(UUID runId) {
        return new JobParametersBuilder().addString(RUN_ID, runId.toString()).toJobParameters();
    }

    public static UUID runIdOf(JobExecution execution) {
        return UUID.fromString(execution.getJobParameters().getString(RUN_ID));
    }

    public static String jobName(RunType type) {
        return switch (type) {
            case OPERATION -> ROW_OPERATION_JOB;
            case AGGREGATION -> AGGREGATION_JOB;
        };
    }

    public static String mainStep(RunType type) {
        return switch (type) {
            case OPERATION -> ROW_OPERATION_STEP;
            case AGGREGATION -> AGGREGATION_STEP;
        };
    }

}
