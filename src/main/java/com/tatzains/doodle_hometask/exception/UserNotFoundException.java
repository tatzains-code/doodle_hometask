package com.tatzains.doodle_hometask.exception;

import java.util.Set;
import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }

    public static UserNotFoundException forId(UUID id) {
        return new UserNotFoundException("User not found: " + id);
    }

    public static UserNotFoundException forIds(Set<UUID> ids) {
        return new UserNotFoundException("Unknown participant id(s): " + ids);
    }
}
