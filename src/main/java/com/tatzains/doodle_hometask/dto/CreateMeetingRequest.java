package com.tatzains.doodle_hometask.dto;

import com.tatzains.doodle_hometask.validation.ValidTimeRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@ValidTimeRange
public record CreateMeetingRequest(
        @NotBlank String title,
        String description,
        @NotNull Instant start,
        @NotNull Instant end,
        @NotEmpty @Size(min = 1) Set<UUID> participantIds
) implements TimeRangeRequest {
}
