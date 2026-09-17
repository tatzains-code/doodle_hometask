package com.tatzains.doodle_hometask.exception;

import java.util.UUID;

public class SlotConflictException extends RuntimeException {

    public SlotConflictException(UUID userId) {
        super("User " + userId + " is not available for the requested time range");
    }
}
