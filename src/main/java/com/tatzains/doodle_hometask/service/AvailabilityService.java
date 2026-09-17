package com.tatzains.doodle_hometask.service;

import com.tatzains.doodle_hometask.config.SchedulingProperties;
import com.tatzains.doodle_hometask.domain.SlotStatus;
import com.tatzains.doodle_hometask.domain.TimeSlot;
import com.tatzains.doodle_hometask.exception.InvalidRangeException;
import com.tatzains.doodle_hometask.exception.UserNotFoundException;
import com.tatzains.doodle_hometask.repository.TimeSlotRepository;
import com.tatzains.doodle_hometask.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final SchedulingProperties schedulingProperties;

    @Transactional(readOnly = true)
    public Page<TimeSlot> getAvailability(UUID ownerId, Instant from, Instant to, Integer minDurationMinutes, Pageable pageable) {
        if (!userRepository.existsById(ownerId)) {
            throw UserNotFoundException.forId(ownerId);
        }

        SchedulingProperties.Availability config = schedulingProperties.getAvailability();
        Instant rangeStart = from != null ? from : Instant.now();
        Instant rangeEnd = to != null ? to : rangeStart.plus(Duration.ofDays(config.getDefaultRangeDays()));

        if (!rangeEnd.isAfter(rangeStart)) {
            throw new InvalidRangeException("'to' must be after 'from'");
        }

        Duration requestedRange = Duration.between(rangeStart, rangeEnd);
        Duration maxRange = Duration.ofDays(config.getMaxRangeDays());
        if (requestedRange.compareTo(maxRange) > 0) {
            throw new InvalidRangeException(
                    "Requested range exceeds the maximum of " + config.getMaxRangeDays() + " days");
        }

        if (minDurationMinutes == null) {
            return timeSlotRepository.findOverlappingPage(ownerId, rangeStart, rangeEnd, pageable);
        }

        // Can't push duration filtering into the paged query without a DB-specific interval
        // expression, so filter in memory and paginate by hand — fine at this project's scale.
        // Merge adjacent FREE slots first, since several back-to-back slots can jointly
        // satisfy a duration that no single slot meets.
        List<TimeSlot> freeSlots = timeSlotRepository.findOverlappingList(ownerId, rangeStart, rangeEnd).stream()
                .filter(slot -> slot.getStatus() == SlotStatus.FREE)
                .toList();
        List<TimeSlot> filtered = mergeAdjacentRuns(freeSlots).stream()
                .filter(run -> Duration.between(run.getStart(), run.getEnd()).toMinutes() >= minDurationMinutes)
                .toList();
        return paginate(filtered, pageable);
    }

    /**
     * Merges back-to-back FREE slots into contiguous runs. {@code slots} must already be
     * sorted by {@code start}. Runs are represented as transient, non-persisted
     * {@link TimeSlot}s to avoid a parallel DTO for this endpoint alone.
     */
    private List<TimeSlot> mergeAdjacentRuns(List<TimeSlot> slots) {
        List<TimeSlot> runs = new ArrayList<>();
        TimeSlot currentRun = null;
        for (TimeSlot slot : slots) {
            if (currentRun != null && currentRun.getEnd().equals(slot.getStart())) {
                currentRun.setEnd(slot.getEnd());
            } else {
                currentRun = TimeSlot.builder()
                        .id(slot.getId())
                        .owner(slot.getOwner())
                        .start(slot.getStart())
                        .end(slot.getEnd())
                        .status(slot.getStatus())
                        .build();
                runs.add(currentRun);
            }
        }
        return runs;
    }

    private Page<TimeSlot> paginate(List<TimeSlot> content, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(content, pageable, content.size());
        }
        int start = Math.min((int) pageable.getOffset(), content.size());
        int end = Math.min(start + pageable.getPageSize(), content.size());
        return new PageImpl<>(content.subList(start, end), pageable, content.size());
    }
}
