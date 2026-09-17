package com.tatzains.doodle_hometask.service;

import com.tatzains.doodle_hometask.domain.User;
import com.tatzains.doodle_hometask.dto.request.CreateUserRequest;
import com.tatzains.doodle_hometask.exception.DuplicateEmailException;
import com.tatzains.doodle_hometask.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .build();

        // saveAndFlush forces the insert (and the email unique-constraint check) to happen
        // here, so a race with another concurrent createUser surfaces as DuplicateEmailException
        // rather than falling through to the generic booking-conflict handling at commit time.
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException(request.email());
        }
    }
}
