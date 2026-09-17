package com.tatzains.doodle_hometask.controller;

import com.tatzains.doodle_hometask.AbstractIntegrationTest;
import com.tatzains.doodle_hometask.dto.response.AvailabilitySlotResponse;
import com.tatzains.doodle_hometask.dto.request.CreateMeetingRequest;
import com.tatzains.doodle_hometask.dto.response.UserResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AvailabilityIntegrationTest extends AbstractIntegrationTest {

    @Test
    void nonOwnerViewingAvailabilityNeverSeesMeetingDetails() throws Exception {
        UserResponse organizer = createUser("Availability Organizer", "org-avail@example.com");
        UserResponse owner = createUser("Availability Owner", "owner-avail@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(20));
        Instant end = start.plus(Duration.ofMinutes(30));
        createSlot(organizer.id(), start, end);
        createSlot(owner.id(), start, end);

        String secretTitle = "Confidential Strategy Review";
        CreateMeetingRequest request = new CreateMeetingRequest(
                secretTitle, "very secret agenda", start, end, Set.of(owner.id()));
        mockMvc.perform(asUser(organizer.id(), postJson("/meetings", request)))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(asUser(organizer.id(), get("/availability")
                        .param("ownerId", owner.id().toString())
                        .param("from", start.minus(Duration.ofMinutes(15)).toString())
                        .param("to", end.plus(Duration.ofMinutes(15)).toString())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(secretTitle);
        assertThat(response).doesNotContain("very secret agenda");
        assertThat(response).doesNotContain("meetingId");
        assertThat(response).doesNotContain("participants");

        List<AvailabilitySlotResponse> slots = readPagedContent(response, AvailabilitySlotResponse.class);
        assertThat(slots).hasSize(1);
        assertThat(slots.getFirst().status().name()).isEqualTo("BUSY");
    }

    @Test
    void rejectsRangeWiderThanMaxRangeDays() throws Exception {
        UserResponse owner = createUser("Range Owner", "owner-range@example.com");
        UserResponse viewer = createUser("Range Viewer", "viewer-range@example.com");

        Instant from = Instant.now();
        Instant to = from.plus(Duration.ofDays(91));

        mockMvc.perform(asUser(viewer.id(), get("/availability")
                        .param("ownerId", owner.id().toString())
                        .param("from", from.toString())
                        .param("to", to.toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultsRangeWhenFromAndToOmitted() throws Exception {
        UserResponse owner = createUser("Default Range Owner", "owner-default@example.com");
        UserResponse viewer = createUser("Default Range Viewer", "viewer-default@example.com");

        Instant withinDefaultRange = alignedFutureStart(Duration.ofDays(2));
        createSlot(owner.id(), withinDefaultRange, withinDefaultRange.plus(Duration.ofMinutes(30)));

        Instant beyondDefaultRange = alignedFutureStart(Duration.ofDays(10));
        createSlot(owner.id(), beyondDefaultRange, beyondDefaultRange.plus(Duration.ofMinutes(30)));

        String response = mockMvc.perform(asUser(viewer.id(), get("/availability")
                        .param("ownerId", owner.id().toString())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<AvailabilitySlotResponse> slots = readPagedContent(response, AvailabilitySlotResponse.class);

        assertThat(slots).extracting(AvailabilitySlotResponse::start).containsExactly(withinDefaultRange);
    }

    @Test
    void mergesContiguousFreeSlotsToSatisfyDurationSearch() throws Exception {
        UserResponse owner = createUser("Contiguous Owner", "owner-contiguous@example.com");
        UserResponse viewer = createUser("Contiguous Viewer", "viewer-contiguous@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(30));
        createSlot(owner.id(), start, start.plus(Duration.ofMinutes(30)));
        createSlot(owner.id(), start.plus(Duration.ofMinutes(30)), start.plus(Duration.ofMinutes(60)));
        createSlot(owner.id(), start.plus(Duration.ofMinutes(60)), start.plus(Duration.ofMinutes(90)));

        String response = mockMvc.perform(asUser(viewer.id(), get("/availability")
                        .param("ownerId", owner.id().toString())
                        .param("from", start.minus(Duration.ofMinutes(15)).toString())
                        .param("to", start.plus(Duration.ofMinutes(105)).toString())
                        .param("duration", "90")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<AvailabilitySlotResponse> slots = readPagedContent(response, AvailabilitySlotResponse.class);

        assertThat(slots).hasSize(1);
        assertThat(slots.getFirst().start()).isEqualTo(start);
        assertThat(slots.getFirst().end()).isEqualTo(start.plus(Duration.ofMinutes(90)));
    }

    @Test
    void doesNotMergeFreeSlotsAcrossAGap() throws Exception {
        UserResponse owner = createUser("Gapped Owner", "owner-gapped@example.com");
        UserResponse viewer = createUser("Gapped Viewer", "viewer-gapped@example.com");

        Instant start = alignedFutureStart(Duration.ofHours(31));
        createSlot(owner.id(), start, start.plus(Duration.ofMinutes(30)));
        createSlot(owner.id(), start.plus(Duration.ofMinutes(30)), start.plus(Duration.ofMinutes(60)));
        // Gap: no slot for [60,75) minutes, so the run below can't join the first two.
        createSlot(owner.id(), start.plus(Duration.ofMinutes(75)), start.plus(Duration.ofMinutes(105)));

        String response = mockMvc.perform(asUser(viewer.id(), get("/availability")
                        .param("ownerId", owner.id().toString())
                        .param("from", start.minus(Duration.ofMinutes(15)).toString())
                        .param("to", start.plus(Duration.ofMinutes(120)).toString())
                        .param("duration", "90")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<AvailabilitySlotResponse> slots = readPagedContent(response, AvailabilitySlotResponse.class);

        assertThat(slots).isEmpty();
    }
}
