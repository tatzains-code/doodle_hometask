package com.tatzains.doodle_hometask.exception;

/** A missing or malformed {@code X-User-Id} header (400) — distinct from {@link UserNotFoundException} (404). */
public class InvalidAuthHeaderException extends RuntimeException {

    public InvalidAuthHeaderException(String message) {
        super(message);
    }
}
