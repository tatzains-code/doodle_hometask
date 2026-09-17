package com.tatzains.doodle_hometask.mapper;

import com.tatzains.doodle_hometask.domain.TimeSlot;
import com.tatzains.doodle_hometask.dto.response.AvailabilitySlotResponse;

/**
 * Maps to the restricted availability projection — kept separate from {@link SlotMapper}
 * so the own-vs-public split is enforced by which mapper is called, not the serializer.
 */
public final class AvailabilityMapper {

    private AvailabilityMapper() {
    }

    public static AvailabilitySlotResponse toResponse(TimeSlot slot) {
        return new AvailabilitySlotResponse(
                slot.getId(),
                slot.getStart(),
                slot.getEnd(),
                slot.getStatus()
        );
    }
}
