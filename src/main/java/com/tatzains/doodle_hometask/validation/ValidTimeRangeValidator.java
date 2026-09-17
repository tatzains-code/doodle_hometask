package com.tatzains.doodle_hometask.validation;

import com.tatzains.doodle_hometask.config.SchedulingProperties;
import com.tatzains.doodle_hometask.dto.request.TimeRangeRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class ValidTimeRangeValidator implements ConstraintValidator<ValidTimeRange, TimeRangeRequest> {

    private final SchedulingProperties schedulingProperties;

    @Override
    public boolean isValid(TimeRangeRequest value, ConstraintValidatorContext context) {
        if (value == null || value.start() == null || value.end() == null) {
            // @NotNull on the individual fields reports this case.
            return true;
        }

        Instant start = value.start();
        Instant end = value.end();
        SchedulingProperties.Slot slot = schedulingProperties.getSlot();

        if (!end.isAfter(start)) {
            return fail(context, "end must be after start");
        }

        long granularitySeconds = Duration.ofMinutes(slot.getGranularityMinutes()).getSeconds();
        Duration duration = Duration.between(start, end);

        if (duration.getSeconds() % granularitySeconds != 0) {
            return fail(context, "duration must be a multiple of " + slot.getGranularityMinutes() + " minutes");
        }

        if (start.getEpochSecond() % granularitySeconds != 0) {
            return fail(context, "start must be aligned to a " + slot.getGranularityMinutes() + "-minute granularity");
        }

        long durationMinutes = duration.toMinutes();
        if (durationMinutes < slot.getMinDurationMinutes() || durationMinutes > slot.getMaxDurationMinutes()) {
            return fail(context, "duration must be between " + slot.getMinDurationMinutes()
                    + " and " + slot.getMaxDurationMinutes() + " minutes");
        }

        Instant now = Instant.now();
        Instant earliestAllowedStart = now.plus(Duration.ofMinutes(slot.getMinBookingBufferMinutes()));
        if (start.isBefore(earliestAllowedStart)) {
            return fail(context, "start must be at least " + slot.getMinBookingBufferMinutes() + " minutes in the future");
        }

        Instant latestAllowedStart = now.plus(Duration.ofDays(slot.getMaxHorizonDays()));
        if (start.isAfter(latestAllowedStart)) {
            return fail(context, "start must be no more than " + slot.getMaxHorizonDays() + " days in the future");
        }

        return true;
    }

    private boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
