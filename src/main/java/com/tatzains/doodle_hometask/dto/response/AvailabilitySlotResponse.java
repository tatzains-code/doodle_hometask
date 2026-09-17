package com.tatzains.doodle_hometask.dto.response;

import com.tatzains.doodle_hometask.domain.SlotStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Restricted projection of another user's slot, returned by {@code GET /availability}.
 * Deliberately excludes any meeting reference: timing and free/busy status only.
 */
public record AvailabilitySlotResponse(
        UUID id,
        Instant start,
        Instant end,
        SlotStatus status
) {
}
