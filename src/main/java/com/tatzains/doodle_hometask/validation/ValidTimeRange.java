package com.tatzains.doodle_hometask.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a {@link com.tatzains.doodle_hometask.dto.TimeRangeRequest}'s start/end
 * against {@link com.tatzains.doodle_hometask.config.SchedulingProperties}: end after
 * start, duration and start aligned to the configured granularity, duration within the
 * configured bounds, and start within the allowed booking window.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTimeRangeValidator.class)
@Documented
public @interface ValidTimeRange {

    String message() default "invalid time range";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
