package com.tatzains.doodle_hometask.repository;

import com.tatzains.doodle_hometask.domain.TimeSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    /**
     * Slots owned by {@code ownerId} that overlap {@code [rangeStart, rangeEnd)}.
     * Used both for conflict checks when booking and for the availability listing.
     */
    @Query("""
            select t from TimeSlot t
            where t.owner.id = :ownerId
              and t.start < :rangeEnd
              and t.end > :rangeStart
            """)
    Page<TimeSlot> findOverlappingPage(
            @Param("ownerId") UUID ownerId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            Pageable pageable);

    default List<TimeSlot> findOverlappingList(UUID ownerId, Instant rangeStart, Instant rangeEnd) {
        Pageable unpagedSortedByStart = Pageable.unpaged(Sort.by("start"));
        return findOverlappingPage(ownerId, rangeStart, rangeEnd, unpagedSortedByStart).getContent();
    }

    /**
     * All slots tied to a meeting (across all participants), with
     * {@code owner} fetched eagerly. Used on cancellation to flip every
     * associated slot back to {@code FREE} and clear {@code meeting_id} in
     * one query, without going through {@code MeetingParticipant}.
     */
    @Query("select t from TimeSlot t join fetch t.owner where t.meeting.id = :meetingId")
    List<TimeSlot> findByMeetingId(@Param("meetingId") UUID meetingId);
}
