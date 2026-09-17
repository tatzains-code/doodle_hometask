package com.tatzains.doodle_hometask.service;

import com.tatzains.doodle_hometask.domain.User;
import com.tatzains.doodle_hometask.exception.InvalidParticipantsException;
import com.tatzains.doodle_hometask.exception.UserNotFoundException;
import com.tatzains.doodle_hometask.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the participant-validation rules, independent of the DB and the
 * booking transaction itself.
 */
@ExtendWith(MockitoExtension.class)
class MeetingRequestValidatorTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MeetingRequestValidator validator;

    private final User organizer = User.builder().id(UUID.randomUUID()).name("Organizer").email("o@example.com").build();

    @Test
    void rejectsMeetingWithOnlyTheOrganizerAsParticipant() {
        when(userRepository.findAllById(any())).thenReturn(List.of(organizer));

        assertThatThrownBy(() -> validator.validate(organizer, Set.of(organizer.getId())))
                .isInstanceOf(InvalidParticipantsException.class);
    }

    @Test
    void rejectsUnknownParticipantId() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> validator.validate(organizer, Set.of(unknownId)))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void acceptsAtLeastOneDistinctParticipant() {
        User participant = User.builder().id(UUID.randomUUID()).name("Participant").email("i@example.com").build();
        when(userRepository.findAllById(any())).thenReturn(List.of(participant));

        Set<UUID> result = validator.validate(organizer, Set.of(participant.getId()));

        assertThat(result).containsExactly(participant.getId());
    }
}
