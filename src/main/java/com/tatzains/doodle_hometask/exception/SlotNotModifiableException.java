package com.tatzains.doodle_hometask.exception;

import java.util.UUID;

public class SlotNotModifiableException extends RuntimeException {

    public SlotNotModifiableException(UUID slotId) {
        super("Slot " + slotId + " cannot be modified because it is not FREE");
    }
}
