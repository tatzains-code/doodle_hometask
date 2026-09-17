package com.tatzains.doodle_hometask.controller;

import com.tatzains.doodle_hometask.AbstractIntegrationTest;
import com.tatzains.doodle_hometask.domain.SlotStatus;
import com.tatzains.doodle_hometask.domain.TimeSlot;
import com.tatzains.doodle_hometask.dto.request.CreateMeetingRequest;
import com.tatzains.doodle_hometask.dto.response.MeetingResponse;
import com.tatzains.doodle_hometask.dto.response.SlotResponse;
import com.tatzains.doodle_hometask.dto.response.UserResponse;
import com.tatzains.doodle_hometask.repository.MeetingParticipantRepository;
import com.tatzains.doodle_hometask.repository.MeetingRepository;
import com.tatzains.doodle_hometask.repository.TimeSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeetingBookingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository meetingParticipantRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Test
    void booksOneToOneMeetingAndBothPartiesSlotsBecomeBusy() throws Exception {
        UserResponse organizer = createUser("Alice", "alice-1on1@example.com");
        UserResponse participant = createUser("Bob", "bob-1on1@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(2));
        Instant end = start.plus(Duration.ofMinutes(60));
        createSlot(organizer.id(), start, end);
        createSlot(participant.id(), start, end);

        CreateMeetingRequest request = new CreateMeetingRequest(
                "1:1 sync", "catch up", start, end, Set.of(participant.id()));

        String response = mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        MeetingResponse meeting = objectMapper.readValue(response, MeetingResponse.class);

        assertThat(meeting.participants()).hasSize(2);

        List<SlotResponse> participantSlots = listOwnSlots(participant.id());
        assertThat(participantSlots).hasSize(1);
        assertThat(participantSlots.getFirst().status()).isEqualTo(SlotStatus.BUSY);
        assertThat(participantSlots.getFirst().meetingId()).isEqualTo(meeting.id());

        List<SlotResponse> organizerSlots = listOwnSlots(organizer.id());
        assertThat(organizerSlots).hasSize(1);
        assertThat(organizerSlots.getFirst().status()).isEqualTo(SlotStatus.BUSY);
        assertThat(organizerSlots.getFirst().meetingId()).isEqualTo(meeting.id());

        assertThat(listMeetings(organizer.id())).extracting(MeetingResponse::id).contains(meeting.id());
        assertThat(listMeetings(participant.id())).extracting(MeetingResponse::id).contains(meeting.id());
    }

    @Test
    void booksGroupMeetingAndAllParticipantsSlotsBecomeBusy() throws Exception {
        UserResponse organizer = createUser("Group Organizer", "org-group@example.com");
        UserResponse participantB = createUser("Group Participant B", "participantb-group@example.com");
        UserResponse participantC = createUser("Group Participant C", "participantc-group@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(3));
        Instant end = start.plus(Duration.ofMinutes(30));
        createSlot(organizer.id(), start, end);
        createSlot(participantB.id(), start, end);
        createSlot(participantC.id(), start, end);

        CreateMeetingRequest request = new CreateMeetingRequest(
                "Group sync", null, start, end, Set.of(participantB.id(), participantC.id()));

        String response = mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        MeetingResponse meeting = objectMapper.readValue(response, MeetingResponse.class);

        assertThat(meeting.participants()).hasSize(3);

        for (UUID userId : List.of(organizer.id(), participantB.id(), participantC.id())) {
            List<SlotResponse> slots = listOwnSlots(userId);
            assertThat(slots).hasSize(1);
            assertThat(slots.getFirst().status()).isEqualTo(SlotStatus.BUSY);
            assertThat(slots.getFirst().meetingId()).isEqualTo(meeting.id());
        }
    }

    @Test
    void absorbsMultipleAdjacentFreeSlotsWithoutCreatingNewSlotOrConflict() throws Exception {
        UserResponse organizer = createUser("Absorb Organizer", "org-absorb@example.com");
        UserResponse participant = createUser("Absorb Participant", "participant-absorb@example.com");

        Instant t0 = alignedFutureStart(Duration.ofHours(4));
        Instant t30 = t0.plus(Duration.ofMinutes(30));
        Instant t60 = t0.plus(Duration.ofMinutes(60));
        Instant t90 = t0.plus(Duration.ofMinutes(90));

        createSlot(organizer.id(), t0, t90);
        createSlot(participant.id(), t0, t30);
        createSlot(participant.id(), t30, t60);
        createSlot(participant.id(), t60, t90);

        CreateMeetingRequest request = new CreateMeetingRequest(
                "Ninety minute sync", null, t0, t90, Set.of(participant.id()));

        String response = mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        MeetingResponse meeting = objectMapper.readValue(response, MeetingResponse.class);

        List<SlotResponse> participantSlots = listOwnSlots(participant.id());
        assertThat(participantSlots).hasSize(3);
        assertThat(participantSlots).allMatch(s -> s.status() == SlotStatus.BUSY);
        assertThat(participantSlots).allMatch(s -> meeting.id().equals(s.meetingId()));
    }

    @Test
    void bookingFailsWhenFreeSlotsOnlyPartiallyCoverRequestedRange() throws Exception {
        UserResponse organizer = createUser("Gap Organizer", "org-gap@example.com");
        UserResponse participant = createUser("Gap Participant", "participant-gap@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(5));
        Instant partialEnd = start.plus(Duration.ofMinutes(30));
        Instant meetingEnd = start.plus(Duration.ofMinutes(60));

        createSlot(organizer.id(), start, meetingEnd);
        // Participant is FREE for only the first half; the rest has no slot (closed-world).
        createSlot(participant.id(), start, partialEnd);

        CreateMeetingRequest request = new CreateMeetingRequest(
                "Sixty minute sync", null, start, meetingEnd, Set.of(participant.id()));

        mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isConflict());

        List<SlotResponse> participantSlots = listOwnSlots(participant.id());
        assertThat(participantSlots).hasSize(1);
        assertThat(participantSlots.getFirst().status()).isEqualTo(SlotStatus.FREE);
        assertThat(participantSlots.getFirst().start()).isEqualTo(start);
        assertThat(participantSlots.getFirst().end()).isEqualTo(partialEnd);
    }

    @Test
    void bookingFailsWhenParticipantHasNoSlotsInRequestedRange() throws Exception {
        UserResponse organizer = createUser("NoSlot Organizer", "org-noslot@example.com");
        UserResponse participant = createUser("NoSlot Participant", "participant-noslot@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(5).plusMinutes(30));
        Instant end = start.plus(Duration.ofMinutes(30));

        createSlot(organizer.id(), start, end);
        // Participant has no TimeSlot covering [start, end) — closed-world: absence != available.

        CreateMeetingRequest request = new CreateMeetingRequest(
                "No slot sync", null, start, end, Set.of(participant.id()));

        mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isConflict());

        assertThat(listOwnSlots(participant.id())).isEmpty();
        assertThat(listOwnSlots(organizer.id())).hasSize(1);
        assertThat(listOwnSlots(organizer.id()).getFirst().status()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    void bookingFailsWhenExistingFreeSlotExtendsBeyondRequestedRange() throws Exception {
        UserResponse organizer = createUser("Overflow Organizer", "org-overflow@example.com");
        UserResponse participant = createUser("Overflow Participant", "participant-overflow@example.com");

        // FREE slot wider than the meeting on both sides — would need splitting (out of scope).
        Instant slotStart = alignedFutureStart(Duration.ofHours(6).plusMinutes(30));
        Instant slotEnd = slotStart.plus(Duration.ofMinutes(90));
        Instant meetingStart = slotStart.plus(Duration.ofMinutes(30));
        Instant meetingEnd = slotStart.plus(Duration.ofMinutes(60));
        createSlot(organizer.id(), meetingStart, meetingEnd);
        createSlot(participant.id(), slotStart, slotEnd);

        CreateMeetingRequest request = new CreateMeetingRequest(
                "Overflow sync", null, meetingStart, meetingEnd, Set.of(participant.id()));

        mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isConflict());

        List<SlotResponse> participantSlots = listOwnSlots(participant.id());
        assertThat(participantSlots).hasSize(1);
        assertThat(participantSlots.getFirst().status()).isEqualTo(SlotStatus.FREE);
        assertThat(participantSlots.getFirst().start()).isEqualTo(slotStart);
        assertThat(participantSlots.getFirst().end()).isEqualTo(slotEnd);
    }

    @Test
    void bookingFailsAtomicallyWhenOneParticipantIsUnavailable() throws Exception {
        UserResponse organizer = createUser("Conflict Organizer", "org-conflict@example.com");
        UserResponse freeParticipant = createUser("Free Participant", "free-conflict@example.com");
        UserResponse busyParticipant = createUser("Busy Participant", "busy-conflict@example.com");
        UserResponse otherOrganizer = createUser("Other Organizer", "other-conflict@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(6));
        Instant end = start.plus(Duration.ofMinutes(30));

        // Pre-occupy busyParticipant for the same window via a separate, unrelated meeting.
        createSlot(otherOrganizer.id(), start, end);
        createSlot(busyParticipant.id(), start, end);
        CreateMeetingRequest priorMeeting = new CreateMeetingRequest(
                "Prior booking", null, start, end, Set.of(busyParticipant.id()));
        mockMvc.perform(asUser(otherOrganizer.id(), postJson("/meetings", priorMeeting)))
                .andExpect(status().isCreated());

        long meetingCountBefore = meetingRepository.count();
        long participantCountBefore = meetingParticipantRepository.count();

        createSlot(organizer.id(), start, end);
        createSlot(freeParticipant.id(), start, end);
        CreateMeetingRequest conflictingRequest = new CreateMeetingRequest(
                "Attempted group sync", null, start, end, Set.of(freeParticipant.id(), busyParticipant.id()));

        mockMvc.perform(asUser(organizer.id(), postJson("/meetings", conflictingRequest)))
                .andExpect(status().isConflict());

        assertThat(meetingRepository.count()).isEqualTo(meetingCountBefore);
        assertThat(meetingParticipantRepository.count()).isEqualTo(participantCountBefore);
        assertThat(listOwnSlots(organizer.id())).hasSize(1);
        assertThat(listOwnSlots(organizer.id()).getFirst().status()).isEqualTo(SlotStatus.FREE);
        assertThat(listOwnSlots(freeParticipant.id())).hasSize(1);
        assertThat(listOwnSlots(freeParticipant.id()).getFirst().status()).isEqualTo(SlotStatus.FREE);
        assertThat(listOwnSlots(busyParticipant.id())).hasSize(1);
        assertThat(listOwnSlots(busyParticipant.id()).getFirst().status()).isEqualTo(SlotStatus.BUSY);
    }

    @Test
    void rejectsMeetingWithEmptyParticipantList() throws Exception {
        UserResponse organizer = createUser("Empty Organizer", "org-empty@example.com");
        Instant start = alignedFutureStart(Duration.ofHours(7));
        Instant end = start.plus(Duration.ofMinutes(30));

        CreateMeetingRequest request = new CreateMeetingRequest("Solo", null, start, end, Set.of());

        mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSelfOnlyMeeting() throws Exception {
        UserResponse organizer = createUser("Self Organizer", "org-self@example.com");
        Instant start = alignedFutureStart(Duration.ofHours(7).plusMinutes(30));
        Instant end = start.plus(Duration.ofMinutes(30));

        CreateMeetingRequest request = new CreateMeetingRequest(
                "Solo", null, start, end, Set.of(organizer.id()));

        mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownParticipant() throws Exception {
        UserResponse organizer = createUser("Unknown Organizer", "org-unknown@example.com");
        Instant start = alignedFutureStart(Duration.ofHours(8));
        Instant end = start.plus(Duration.ofMinutes(30));

        CreateMeetingRequest request = new CreateMeetingRequest(
                "Ghost invite", null, start, end, Set.of(UUID.randomUUID()));

        mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancellingMeetingReturnsAllParticipantsSlotsToFree() throws Exception {
        UserResponse organizer = createUser("Cancel Organizer", "org-cancel@example.com");
        UserResponse participantB = createUser("Cancel Participant B", "participantb-cancel@example.com");
        UserResponse participantC = createUser("Cancel Participant C", "participantc-cancel@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(9));
        Instant end = start.plus(Duration.ofMinutes(30));
        createSlot(organizer.id(), start, end);
        createSlot(participantB.id(), start, end);
        createSlot(participantC.id(), start, end);

        CreateMeetingRequest request = new CreateMeetingRequest(
                "Cancel me", null, start, end, Set.of(participantB.id(), participantC.id()));

        String response = mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        MeetingResponse meeting = objectMapper.readValue(response, MeetingResponse.class);

        mockMvc.perform(asUser(organizer.id(), delete("/meetings/" + meeting.id())))
                .andExpect(status().isNoContent());

        for (UUID userId : List.of(organizer.id(), participantB.id(), participantC.id())) {
            List<SlotResponse> slots = listOwnSlots(userId);
            assertThat(slots).hasSize(1);
            assertThat(slots.getFirst().status()).isEqualTo(SlotStatus.FREE);
            assertThat(slots.getFirst().meetingId()).isNull();
        }
    }

    @Test
    void onlyOrganizerCanCancelMeeting() throws Exception {
        UserResponse organizer = createUser("Auth Organizer", "org-auth@example.com");
        UserResponse participant = createUser("Auth Participant", "participant-auth@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(10));
        Instant end = start.plus(Duration.ofMinutes(30));
        createSlot(organizer.id(), start, end);
        createSlot(participant.id(), start, end);

        CreateMeetingRequest request = new CreateMeetingRequest(
                "Owner only cancel", null, start, end, Set.of(participant.id()));

        String response = mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        MeetingResponse meeting = objectMapper.readValue(response, MeetingResponse.class);

        mockMvc.perform(asUser(participant.id(), delete("/meetings/" + meeting.id())))
                .andExpect(status().isForbidden());
    }

    /**
     * Two organizers race to book the same participant for overlapping time — exactly one
     * should win, the other 409s. Optional per the brief; not worth chasing if flaky.
     */
    @Test
    void concurrentBookingsForSharedParticipantOnlyOneSucceeds() throws Exception {
        UserResponse organizerA = createUser("Racer A", "racera@example.com");
        UserResponse organizerB = createUser("Racer B", "racerb@example.com");
        UserResponse sharedParticipant = createUser("Shared Participant", "shared-race@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(11));
        Instant end = start.plus(Duration.ofMinutes(30));
        createSlot(organizerA.id(), start, end);
        createSlot(organizerB.id(), start, end);
        createSlot(sharedParticipant.id(), start, end);

        CreateMeetingRequest requestA = new CreateMeetingRequest(
                "Race A", null, start, end, Set.of(sharedParticipant.id()));
        CreateMeetingRequest requestB = new CreateMeetingRequest(
                "Race B", null, start, end, Set.of(sharedParticipant.id()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<Integer> resultA = executor.submit(() -> fireBooking(organizerA.id(), requestA, readyLatch, startLatch));
            Future<Integer> resultB = executor.submit(() -> fireBooking(organizerB.id(), requestB, readyLatch, startLatch));

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            int statusA = resultA.get(10, TimeUnit.SECONDS);
            int statusB = resultB.get(10, TimeUnit.SECONDS);

            List<TimeSlot> finalSlots = timeSlotRepository
                    .findByOwnerId(sharedParticipant.id(), Pageable.unpaged(Sort.by("start")))
                    .getContent();

            assertThat(List.of(statusA, statusB)).containsExactlyInAnyOrder(201, 409);
            assertThat(finalSlots).hasSize(1);
            assertThat(finalSlots.getFirst().getStatus()).isEqualTo(SlotStatus.BUSY);
        } finally {
            executor.shutdownNow();
        }
    }

    private int fireBooking(UUID organizerId, CreateMeetingRequest request, CountDownLatch readyLatch, CountDownLatch startLatch) throws Exception {
        readyLatch.countDown();
        startLatch.await();
        return mockMvc.perform(asUser(organizerId, postJson("/meetings", request)))
                .andReturn().getResponse().getStatus();
    }

    private List<SlotResponse> listOwnSlots(UUID userId) throws Exception {
        String response = mockMvc.perform(asUser(userId, get("/slots")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readPagedContent(response, SlotResponse.class);
    }

    private List<MeetingResponse> listMeetings(UUID userId) throws Exception {
        String response = mockMvc.perform(asUser(userId, get("/meetings")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readPagedContent(response, MeetingResponse.class);
    }
}
