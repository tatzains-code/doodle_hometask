package com.tatzains.doodle_hometask.dto;

import com.tatzains.doodle_hometask.validation.ValidTimeRange;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@ValidTimeRange
public record CreateSlotRequest(
        @NotNull Instant start,
        @NotNull Instant end
) implements TimeRangeRequest {
}
