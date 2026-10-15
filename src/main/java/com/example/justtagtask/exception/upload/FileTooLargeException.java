package com.example.justtagtask.exception.upload;

import com.example.justtagtask.exception.common.ApiException;
import org.springframework.http.HttpStatus;

public class FileTooLargeException extends ApiException {

    public FileTooLargeException(long maxBytes) {
        super(HttpStatus.CONTENT_TOO_LARGE, "The file is larger than the limit of " + maxBytes + " bytes");
    }

}
