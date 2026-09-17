package com.tatzains.doodle_hometask.dto;

import java.time.Instant;

/**
 * Common shape for requests validated by {@code @ValidTimeRange}, so the
 * validator can read start/end without knowing the concrete request type.
 */
public interface TimeRangeRequest {

    Instant start();

    Instant end();
}
