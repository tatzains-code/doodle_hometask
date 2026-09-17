package com.tatzains.doodle_hometask.dto.response;

import com.tatzains.doodle_hometask.domain.SlotStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Full-detail view of a slot the requester owns. Never returned for another user's
 * slots — see {@link AvailabilitySlotResponse} for the restricted public view.
 */
public record SlotResponse(
        UUID id,
        Instant start,
        Instant end,
        SlotStatus status,
        UUID meetingId
) {
}
