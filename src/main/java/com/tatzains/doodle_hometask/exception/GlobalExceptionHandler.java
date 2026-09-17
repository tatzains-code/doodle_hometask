package com.tatzains.doodle_hometask.exception;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Stream;

/**
 * Single place mapping domain/validation exceptions to RFC 7807 {@link ProblemDetail}
 * responses, so controllers never build error bodies themselves.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Counts a booking rejected for participant unavailability, app-detected or DB-constraint. */
    private static final String BOOKING_CONFLICT_COUNTER = "meeting.booking.conflict";

    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler({UserNotFoundException.class, SlotNotFoundException.class, MeetingNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    @ExceptionHandler({InvalidParticipantsException.class, InvalidRangeException.class, InvalidAuthHeaderException.class})
    public ProblemDetail handleBadRequest(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(SlotNotModifiableException.class)
    public ProblemDetail handleNotModifiable(SlotNotModifiableException ex) {
        return problem(HttpStatus.CONFLICT, "Slot not modifiable", ex.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        return problem(HttpStatus.CONFLICT, "Email already in use", ex.getMessage());
    }

    @ExceptionHandler(SlotConflictException.class)
    public ProblemDetail handleSlotConflict(SlotConflictException ex) {
        meterRegistry.counter(BOOKING_CONFLICT_COUNTER).increment();
        return problem(HttpStatus.CONFLICT, "Slot unavailable", ex.getMessage());
    }

    @ExceptionHandler(NotMeetingOwnerException.class)
    public ProblemDetail handleForbidden(NotMeetingOwnerException ex) {
        return problem(HttpStatus.FORBIDDEN, "Not authorized", ex.getMessage());
    }

    /**
     * Safety net for races the upfront check can miss: the GiST exclusion constraint
     * surfaces as {@link DataIntegrityViolationException}, an optimistic-lock failure as
     * {@link ConcurrencyFailureException}. Both map to 409, not a leaked 500.
     */
    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ProblemDetail handleDataConflict(Exception ex) {
        meterRegistry.counter(BOOKING_CONFLICT_COUNTER).increment();
        return problem(HttpStatus.CONFLICT, "Slot unavailable", "The requested slot(s) are no longer available");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage()),
                        // Class-level constraints (e.g. @ValidTimeRange) land here, not in getFieldErrors().
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(ObjectError::getDefaultMessage))
                .toList();

        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                errors.isEmpty() ? "Request validation failed" : String.join("; ", errors));
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "Malformed request body");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Missing parameter", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter", "Invalid value for parameter: " + ex.getName());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }
}
