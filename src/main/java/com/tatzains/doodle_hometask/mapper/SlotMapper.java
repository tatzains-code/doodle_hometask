package com.tatzains.doodle_hometask.mapper;

import com.tatzains.doodle_hometask.domain.TimeSlot;
import com.tatzains.doodle_hometask.dto.response.SlotResponse;

public final class SlotMapper {

    private SlotMapper() {
    }

    public static SlotResponse toResponse(TimeSlot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getStart(),
                slot.getEnd(),
                slot.getStatus(),
                slot.getMeeting() != null ? slot.getMeeting().getId() : null
        );
    }
}
