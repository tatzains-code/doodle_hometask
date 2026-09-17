package com.tatzains.doodle_hometask.web;

import com.tatzains.doodle_hometask.domain.User;
import com.tatzains.doodle_hometask.exception.InvalidAuthHeaderException;
import com.tatzains.doodle_hometask.exception.UserNotFoundException;
import com.tatzains.doodle_hometask.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the "current user" from the {@code X-User-Id} header — the auth stand-in for
 * this project (no password, no session).
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final UserRepository userRepository;

    public User resolve(HttpServletRequest request) {
        String header = request.getHeader(USER_ID_HEADER);
        if (header == null || header.isBlank()) {
            throw new InvalidAuthHeaderException("Missing required header: " + USER_ID_HEADER);
        }

        UUID userId;
        try {
            userId = UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidAuthHeaderException("Invalid " + USER_ID_HEADER + " header: " + header);
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.forId(userId));
    }
}
