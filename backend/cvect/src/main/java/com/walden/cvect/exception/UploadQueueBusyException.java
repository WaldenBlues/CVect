package com.walden.cvect.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class UploadQueueBusyException extends RuntimeException {

    public UploadQueueBusyException(String message) {
        super(message);
    }
}
