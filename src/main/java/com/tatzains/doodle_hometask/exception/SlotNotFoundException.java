package com.tatzains.doodle_hometask.exception;

import java.util.UUID;

public class SlotNotFoundException extends RuntimeException {

    public SlotNotFoundException(UUID slotId) {
        super("Slot not found: " + slotId);
    }
}
