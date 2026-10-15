package com.example.justtagtask.exception.processing;

import com.example.justtagtask.exception.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidProcessingRequestException extends ApiException {

    public InvalidProcessingRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
    }

}
