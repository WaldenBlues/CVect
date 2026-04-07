package com.walden.cvect.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidUploadRequestException extends RuntimeException {

    public InvalidUploadRequestException(String message) {
        super(message);
    }
}
