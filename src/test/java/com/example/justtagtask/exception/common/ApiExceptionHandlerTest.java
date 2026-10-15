package com.example.justtagtask.exception.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();

    @Test
    void mapsAJobThatCannotStartToConflictWithItsMessage() {
        ProblemDetail problem = apiExceptionHandler.jobCannotStart(new JobInstanceAlreadyCompleteException("The job is already complete"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("The job is already complete");
    }

    @Test
    void mapsAConcurrentRunToConflict() {
        ProblemDetail problem = apiExceptionHandler.operationAlreadyInProgress();

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("Another operation on this file is already in progress");
    }

}
