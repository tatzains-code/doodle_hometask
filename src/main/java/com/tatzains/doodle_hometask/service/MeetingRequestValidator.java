package com.tatzains.doodle_hometask.service;

import com.tatzains.doodle_hometask.domain.User;
import com.tatzains.doodle_hometask.exception.InvalidParticipantsException;
import com.tatzains.doodle_hometask.exception.UserNotFoundException;
import com.tatzains.doodle_hometask.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validates a meeting-booking request's participant list, independent of the booking
 * transaction itself so it can be unit tested without a database.
 */
@Component
@RequiredArgsConstructor
public class MeetingRequestValidator {

    private final UserRepository userRepository;

    /**
     * @return the deduplicated set of requested participant ids (organizer excluded),
     * after confirming they all exist and at least one is distinct from the organizer.
     */
    public Set<UUID> validate(User organizer, Set<UUID> participantIds) {
        Set<UUID> deduped = new LinkedHashSet<>(participantIds);

        List<User> found = userRepository.findAllById(deduped);
        if (found.size() != deduped.size()) {
            Set<UUID> foundIds = found.stream().map(User::getId).collect(Collectors.toSet());
            Set<UUID> missing = deduped.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            throw UserNotFoundException.forIds(missing);
        }

        Set<UUID> otherThanOrganizer = deduped.stream()
                .filter(id -> !id.equals(organizer.getId()))
                .collect(Collectors.toSet());
        if (otherThanOrganizer.isEmpty()) {
            throw new InvalidParticipantsException(
                    "A meeting must include at least one participant other than the organizer");
        }

        return deduped;
    }
}
