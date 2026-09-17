package com.tatzains.doodle_hometask.dto.response;

import com.tatzains.doodle_hometask.domain.ParticipantRole;

import java.util.UUID;

public record ParticipantResponse(
        UUID userId,
        String name,
        ParticipantRole role
) {
}
