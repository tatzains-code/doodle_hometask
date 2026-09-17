package com.tatzains.doodle_hometask.repository;

import com.tatzains.doodle_hometask.domain.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, UUID> {

    @Query("select mp from MeetingParticipant mp join fetch mp.meeting where mp.user.id = :userId")
    List<MeetingParticipant> findByUserId(@Param("userId") UUID userId);
}
