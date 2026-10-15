package com.example.justtagtask.exception.upload;

import com.example.justtagtask.exception.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidUploadException extends ApiException {

    public InvalidUploadException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
    }

}
