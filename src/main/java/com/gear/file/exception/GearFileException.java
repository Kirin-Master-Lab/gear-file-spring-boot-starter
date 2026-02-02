package com.gear.file.exception;

public class GearFileException extends RuntimeException {

    public GearFileException(String message) {
        super(message);
    }

    public GearFileException(String message, Throwable cause) {
        super(message, cause);
    }
}