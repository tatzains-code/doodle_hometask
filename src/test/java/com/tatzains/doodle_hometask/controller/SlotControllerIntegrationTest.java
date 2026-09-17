package com.tatzains.doodle_hometask.controller;

import com.tatzains.doodle_hometask.AbstractIntegrationTest;
import com.tatzains.doodle_hometask.dto.request.CreateMeetingRequest;
import com.tatzains.doodle_hometask.dto.response.SlotResponse;
import com.tatzains.doodle_hometask.dto.request.UpdateSlotRequest;
import com.tatzains.doodle_hometask.dto.response.UserResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

class SlotControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void missingUserIdHeaderReturns400NotNotFound() throws Exception {
        mockMvc.perform(get("/slots"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedUserIdHeaderReturns400NotNotFound() throws Exception {
        mockMvc.perform(get("/slots").header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownUserIdHeaderReturns404() throws Exception {
        mockMvc.perform(get("/slots").header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchOnBusySlotReturns409() throws Exception {
        UserResponse organizer = createUser("Patch Organizer", "org-patch@example.com");
        UserResponse owner = createUser("Patch Owner", "owner-patch@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(12));
        Instant end = start.plus(Duration.ofMinutes(30));
        SlotResponse slot = createSlot(owner.id(), start, end);

        bookMeeting(organizer.id(), owner.id(), start, end);

        UpdateSlotRequest update = new UpdateSlotRequest(start.plus(Duration.ofMinutes(15)), end.plus(Duration.ofMinutes(15)));
        mockMvc.perform(asUser(owner.id(), patchJson("/slots/" + slot.id(), update)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteOnBusySlotReturns409() throws Exception {
        UserResponse organizer = createUser("Delete Organizer", "org-delete@example.com");
        UserResponse owner = createUser("Delete Owner", "owner-delete@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(13));
        Instant end = start.plus(Duration.ofMinutes(30));
        SlotResponse slot = createSlot(owner.id(), start, end);

        bookMeeting(organizer.id(), owner.id(), start, end);

        mockMvc.perform(asUser(owner.id(), delete("/slots/" + slot.id())))
                .andExpect(status().isConflict());
    }

    @Test
    void canPatchAndDeleteAFreeSlot() throws Exception {
        UserResponse owner = createUser("Free Owner", "owner-free@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(14));
        Instant end = start.plus(Duration.ofMinutes(30));
        SlotResponse slot = createSlot(owner.id(), start, end);

        UpdateSlotRequest update = new UpdateSlotRequest(start.plus(Duration.ofMinutes(15)), end.plus(Duration.ofMinutes(15)));
        mockMvc.perform(asUser(owner.id(), patchJson("/slots/" + slot.id(), update)))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(owner.id(), delete("/slots/" + slot.id())))
                .andExpect(status().isNoContent());
    }

    private void bookMeeting(UUID organizerId, UUID participantId, Instant start, Instant end) throws Exception {
        createSlot(organizerId, start, end);
        CreateMeetingRequest request = new CreateMeetingRequest("Booking", null, start, end, Set.of(participantId));
        mockMvc.perform(asUser(organizerId, postJson("/meetings", request)))
                .andExpect(status().isCreated());
    }
}
