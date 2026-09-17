package com.tatzains.doodle_hometask.repository;

import com.tatzains.doodle_hometask.domain.MeetingParticipant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, UUID> {

    /**
     * One row per meeting the user is in (organizer or participant) — backs {@code GET /meetings}.
     */
    @Query(
            value = "select mp from MeetingParticipant mp join fetch mp.meeting where mp.user.id = :userId",
            countQuery = "select count(mp) from MeetingParticipant mp where mp.user.id = :userId")
    Page<MeetingParticipant> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("select mp from MeetingParticipant mp join fetch mp.user where mp.meeting.id = :meetingId")
    List<MeetingParticipant> findByMeetingId(@Param("meetingId") UUID meetingId);

    /**
     * Every participant row for a batch of meetings in one query, so a meeting listing can
     * fetch all its meetings' participants together instead of one query per meeting.
     */
    @Query("select mp from MeetingParticipant mp join fetch mp.user where mp.meeting.id in :meetingIds")
    List<MeetingParticipant> findByMeetingIdIn(@Param("meetingIds") Collection<UUID> meetingIds);
}
