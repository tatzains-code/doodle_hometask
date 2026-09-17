package com.tatzains.doodle_hometask.service;

import com.tatzains.doodle_hometask.domain.User;
import com.tatzains.doodle_hometask.exception.InvalidParticipantsException;
import com.tatzains.doodle_hometask.exception.UserNotFoundException;
import com.tatzains.doodle_hometask.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
     * @return the deduplicated requested participants (organizer excluded, unless they also
     * named themselves), keyed by id, after confirming they all exist and at least one is
     * distinct from the organizer. Fetches each participant's {@link User} exactly once, so
     * the caller doesn't need to re-fetch them for the booking itself.
     */
    public Map<UUID, User> validate(User organizer, Set<UUID> participantIds) {
        Set<UUID> deduped = new LinkedHashSet<>(participantIds);

        Map<UUID, User> usersById = userRepository.findAllById(deduped).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a, LinkedHashMap::new));
        if (usersById.size() != deduped.size()) {
            Set<UUID> missing = deduped.stream()
                    .filter(id -> !usersById.containsKey(id))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            throw UserNotFoundException.forIds(missing);
        }

        boolean hasOtherThanOrganizer = deduped.stream().anyMatch(id -> !id.equals(organizer.getId()));
        if (!hasOtherThanOrganizer) {
            throw new InvalidParticipantsException(
                    "A meeting must include at least one participant other than the organizer");
        }

        return usersById;
    }
}
