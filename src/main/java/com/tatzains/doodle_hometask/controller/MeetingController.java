package com.tatzains.doodle_hometask.controller;

import com.tatzains.doodle_hometask.domain.User;
import com.tatzains.doodle_hometask.dto.request.CreateMeetingRequest;
import com.tatzains.doodle_hometask.dto.response.MeetingResponse;
import com.tatzains.doodle_hometask.service.MeetingService;
import com.tatzains.doodle_hometask.web.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Meetings", description = "Atomic multi-participant meeting booking and cancellation")
@RestController
@RequestMapping("/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "Book a meeting for the organizer and every participant, or fail entirely",
            description = "Checks every participantId's availability for [start, end] and books it for "
                    + "all of them in one atomic operation. If any participant is unavailable, nothing "
                    + "is created or booked for anyone.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Meeting booked; every participant's slot is now BUSY"),
            @ApiResponse(responseCode = "400", description = "Bean validation failure, invalid time range, "
                    + "or fewer than one participant distinct from the organizer"),
            @ApiResponse(responseCode = "404", description = "The organizer or a participantId does not exist"),
            @ApiResponse(responseCode = "409", description = "At least one participant already has a "
                    + "conflicting BUSY slot for the requested time range")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingResponse createMeeting(HttpServletRequest httpRequest, @Valid @RequestBody CreateMeetingRequest request) {
        User organizer = currentUserResolver.resolve(httpRequest);
        return meetingService.bookMeeting(organizer, request);
    }

    @Operation(summary = "List meetings where the current user is organizer or participant")
    @ApiResponse(responseCode = "200", description = "Meetings returned")
    @GetMapping
    public PagedModel<MeetingResponse> listMeetings(
            HttpServletRequest httpRequest,
            @PageableDefault(size = 50) Pageable pageable) {
        User user = currentUserResolver.resolve(httpRequest);
        return new PagedModel<>(meetingService.listMeetingsForUser(user, pageable));
    }

    @Operation(summary = "Cancel a meeting; every participant's slot returns to FREE",
            description = "Only the meeting's OWNER participant (the organizer) may cancel it.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Meeting cancelled"),
            @ApiResponse(responseCode = "403", description = "Current user is not this meeting's organizer"),
            @ApiResponse(responseCode = "404", description = "Meeting does not exist")
    })
    @DeleteMapping("/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMeeting(HttpServletRequest httpRequest, @PathVariable UUID meetingId) {
        User user = currentUserResolver.resolve(httpRequest);
        meetingService.cancelMeeting(user, meetingId);
    }
}
