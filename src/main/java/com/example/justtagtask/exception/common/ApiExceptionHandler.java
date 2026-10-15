package com.example.justtagtask.exception.common;

import org.apache.commons.fileupload2.core.FileUploadException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(JobExecutionNotRunningException.class)
    ProblemDetail jobNotRunning() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The job isn't running");
    }

    @ExceptionHandler({
            JobExecutionAlreadyRunningException.class,
            JobInstanceAlreadyCompleteException.class,
            JobRestartException.class
    })
    ProblemDetail jobCannotStart(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail operationAlreadyInProgress() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Another operation on this file is already in progress");
    }

    @ExceptionHandler(FileUploadException.class)
    ProblemDetail unreadableMultipart(FileUploadException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Could not read the multipart request: " + exception.getMessage());
    }

}
