package com.tatzains.doodle_hometask.exception;

import java.util.UUID;

public class NotMeetingOwnerException extends RuntimeException {

    public NotMeetingOwnerException(UUID meetingId) {
        super("Only the organizer can perform this action on meeting " + meetingId);
    }
}
