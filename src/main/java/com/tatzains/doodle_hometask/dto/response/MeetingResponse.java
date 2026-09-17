package com.tatzains.doodle_hometask.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeetingResponse(
        UUID id,
        String title,
        String description,
        Instant start,
        Instant end,
        List<ParticipantResponse> participants
) {
}
