package com.example.justtagtask.service.loading;

import java.util.UUID;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

public final class CanonicalLoadJobParameters {

    public static final String JOB_NAME = "loadFileJob";
    public static final String STEP_NAME = "loadRecordsStep";
    public static final String FILE_ID = "fileId";

    private CanonicalLoadJobParameters() {
    }

    public static JobParameters forFile(UUID fileId) {
        return new JobParametersBuilder().addString(FILE_ID, fileId.toString()).toJobParameters();
    }

    public static UUID fileIdOf(JobExecution execution) {
        return UUID.fromString(execution.getJobParameters().getString(FILE_ID));
    }

}
