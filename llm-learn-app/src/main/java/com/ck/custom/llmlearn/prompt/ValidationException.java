package com.ck.custom.llmlearn.prompt;

/**
 * @author changkong
 * @date 2026/4/29 23:24
 **/
public class ValidationException extends RuntimeException{

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
