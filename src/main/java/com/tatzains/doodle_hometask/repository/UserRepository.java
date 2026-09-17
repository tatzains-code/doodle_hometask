package com.tatzains.doodle_hometask.repository;

import com.tatzains.doodle_hometask.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);
}
