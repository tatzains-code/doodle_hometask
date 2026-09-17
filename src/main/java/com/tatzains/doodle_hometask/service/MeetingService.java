package com.tatzains.doodle_hometask.service;

import com.tatzains.doodle_hometask.domain.Meeting;
import com.tatzains.doodle_hometask.domain.MeetingParticipant;
import com.tatzains.doodle_hometask.domain.ParticipantRole;
import com.tatzains.doodle_hometask.domain.SlotStatus;
import com.tatzains.doodle_hometask.domain.TimeSlot;
import com.tatzains.doodle_hometask.domain.User;
import com.tatzains.doodle_hometask.dto.request.CreateMeetingRequest;
import com.tatzains.doodle_hometask.dto.response.MeetingResponse;
import com.tatzains.doodle_hometask.exception.MeetingNotFoundException;
import com.tatzains.doodle_hometask.exception.NotMeetingOwnerException;
import com.tatzains.doodle_hometask.exception.SlotConflictException;
import com.tatzains.doodle_hometask.mapper.MeetingMapper;
import com.tatzains.doodle_hometask.repository.MeetingParticipantRepository;
import com.tatzains.doodle_hometask.repository.MeetingRepository;
import com.tatzains.doodle_hometask.repository.TimeSlotRepository;
import com.tatzains.doodle_hometask.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRequestValidator meetingRequestValidator;
    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Books a meeting for the organizer and every requested participant, or fails entirely.
     * Single transaction, so any conflict rolls back the whole thing — no partial booking.
     */
    @Transactional
    public MeetingResponse bookMeeting(User organizer, CreateMeetingRequest request) {
        Set<UUID> requestedParticipantIds = meetingRequestValidator.validate(organizer, request.participantIds());

        Map<UUID, ParticipantRole> roleByUserId = new LinkedHashMap<>();
        roleByUserId.put(organizer.getId(), ParticipantRole.OWNER);
        for (UUID id : requestedParticipantIds) {
            roleByUserId.putIfAbsent(id, ParticipantRole.PARTICIPANT);
        }

        Map<UUID, User> usersById = new LinkedHashMap<>();
        usersById.put(organizer.getId(), organizer);
        for (User user : userRepository.findAllById(roleByUserId.keySet())) {
            usersById.put(user.getId(), user);
        }

        // All-or-nothing: check every participant before mutating anything.
        for (UUID userId : roleByUserId.keySet()) {
            if (!isFullyCovered(userId, request.start(), request.end())) {
                throw new SlotConflictException(userId);
            }
        }

        Meeting meeting = meetingRepository.save(Meeting.builder()
                .title(request.title())
                .description(request.description())
                .start(request.start())
                .end(request.end())
                .build());

        for (UUID userId : roleByUserId.keySet()) {
            bookParticipantSlots(usersById.get(userId), meeting, request.start(), request.end());
        }

        List<MeetingParticipant> participants = roleByUserId.entrySet().stream()
                .map(entry -> MeetingParticipant.builder()
                        .meeting(meeting)
                        .user(usersById.get(entry.getKey()))
                        .role(entry.getValue())
                        .build())
                .toList();
        meetingParticipantRepository.saveAll(participants);

        meterRegistry.counter("meeting.booked").increment();
        return MeetingMapper.toResponse(meeting, participants);
    }

    /**
     * Closed-world availability: a participant is covered for [start, end] only if their
     * existing FREE slots contiguously span it exactly — a gap, a BUSY slot, a slot poking
     * outside the range, or no slots at all all count as not covered.
     */
    private boolean isFullyCovered(UUID userId, Instant start, Instant end) {
        List<TimeSlot> overlapping = new ArrayList<>(timeSlotRepository.findOverlappingList(userId, start, end));
        overlapping.sort(Comparator.comparing(TimeSlot::getStart));

        Instant cursor = start;
        for (TimeSlot slot : overlapping) {
            if (slot.getStatus() == SlotStatus.BUSY
                    || slot.getStart().isBefore(start)
                    || slot.getEnd().isAfter(end)
                    || slot.getStart().isAfter(cursor)) {
                return false;
            }
            cursor = slot.getEnd();
        }
        return !cursor.isBefore(end);
    }

    /**
     * Transitions a participant's FREE slots covering [start, end] to BUSY, without altering
     * their bounds. Re-validates coverage rather than trusting the upfront check, to catch a
     * conflicting slot committed in between.
     */
    private void bookParticipantSlots(User participant, Meeting meeting, Instant start, Instant end) {
        List<TimeSlot> existing = new ArrayList<>(
                timeSlotRepository.findOverlappingList(participant.getId(), start, end));
        existing.sort(Comparator.comparing(TimeSlot::getStart));

        Instant cursor = start;
        for (TimeSlot slot : existing) {
            if (slot.getStatus() == SlotStatus.BUSY
                    || slot.getStart().isBefore(start)
                    || slot.getEnd().isAfter(end)
                    || slot.getStart().isAfter(cursor)) {
                throw new SlotConflictException(participant.getId());
            }

            slot.setStatus(SlotStatus.BUSY);
            slot.setMeeting(meeting);
            cursor = slot.getEnd();
        }

        if (cursor.isBefore(end)) {
            throw new SlotConflictException(participant.getId());
        }

        timeSlotRepository.saveAll(existing);
    }

    @Transactional(readOnly = true)
    public Page<MeetingResponse> listMeetingsForUser(User user, Pageable pageable) {
        Page<MeetingParticipant> ownParticipations = meetingParticipantRepository.findByUserId(user.getId(), pageable);

        List<UUID> meetingIds = ownParticipations.getContent().stream()
                .map(mp -> mp.getMeeting().getId())
                .toList();
        // One batched query for every meeting on this page, instead of one query per meeting.
        Map<UUID, List<MeetingParticipant>> participantsByMeetingId = meetingIds.isEmpty()
                ? Map.of()
                : meetingParticipantRepository.findByMeetingIdIn(meetingIds).stream()
                        .collect(Collectors.groupingBy(mp -> mp.getMeeting().getId()));

        return ownParticipations.map(mp -> {
            Meeting meeting = mp.getMeeting();
            return MeetingMapper.toResponse(meeting, participantsByMeetingId.getOrDefault(meeting.getId(), List.of()));
        });
    }

    @Transactional
    public void cancelMeeting(User currentUser, UUID meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));

        List<MeetingParticipant> participants = meetingParticipantRepository.findByMeetingId(meetingId);
        boolean isOwner = participants.stream()
                .anyMatch(p -> p.getRole() == ParticipantRole.OWNER && p.getUser().getId().equals(currentUser.getId()));
        if (!isOwner) {
            throw new NotMeetingOwnerException(meetingId);
        }

        List<TimeSlot> slots = timeSlotRepository.findByMeetingId(meetingId);
        for (TimeSlot slot : slots) {
            slot.setStatus(SlotStatus.FREE);
            slot.setMeeting(null);
        }
        timeSlotRepository.saveAll(slots);

        meterRegistry.counter("meeting.cancelled").increment();
    }
}
