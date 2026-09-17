package com.tatzains.doodle_hometask.repository;

import com.tatzains.doodle_hometask.domain.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {
}
