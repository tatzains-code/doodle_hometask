package com.tatzains.doodle_hometask.controller;

import com.tatzains.doodle_hometask.domain.User;
import com.tatzains.doodle_hometask.dto.request.CreateSlotRequest;
import com.tatzains.doodle_hometask.dto.response.SlotResponse;
import com.tatzains.doodle_hometask.dto.request.UpdateSlotRequest;
import com.tatzains.doodle_hometask.service.SlotService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Slots", description = "Managing the current user's own FREE/BUSY time slots")
@RestController
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "Create a FREE slot owned by the current user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Slot created"),
            @ApiResponse(responseCode = "400", description = "Invalid time range (misaligned, too short/long, "
                    + "in the past, or too far in the future)"),
            @ApiResponse(responseCode = "409", description = "Slot overlaps one of the owner's existing slots")
    })
    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse createSlot(HttpServletRequest httpRequest, @Valid @RequestBody CreateSlotRequest request) {
        User owner = currentUserResolver.resolve(httpRequest);
        return slotService.createSlot(owner, request);
    }

    @Operation(summary = "List the current user's own slots, in full detail")
    @ApiResponse(responseCode = "200", description = "Slots returned")
    @GetMapping("/slots")
    public PagedModel<SlotResponse> listSlots(
            HttpServletRequest httpRequest,
            @PageableDefault(size = 50, sort = "start") Pageable pageable) {
        User owner = currentUserResolver.resolve(httpRequest);
        return new PagedModel<>(slotService.listOwnSlots(owner, pageable));
    }

    @Operation(summary = "Modify a slot's time range", description = "Only permitted while the slot is FREE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slot updated"),
            @ApiResponse(responseCode = "400", description = "Invalid time range"),
            @ApiResponse(responseCode = "404", description = "Slot does not exist or is not owned by the current user"),
            @ApiResponse(responseCode = "409", description = "Slot is BUSY and cannot be modified")
    })
    @PatchMapping("/slots/{slotId}")
    public SlotResponse updateSlot(
            HttpServletRequest httpRequest,
            @PathVariable UUID slotId,
            @Valid @RequestBody UpdateSlotRequest request) {
        User owner = currentUserResolver.resolve(httpRequest);
        return slotService.updateSlot(owner, slotId, request);
    }

    @Operation(summary = "Delete a slot", description = "Only permitted while the slot is FREE.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Slot deleted"),
            @ApiResponse(responseCode = "404", description = "Slot does not exist or is not owned by the current user"),
            @ApiResponse(responseCode = "409", description = "Slot is BUSY and cannot be deleted")
    })
    @DeleteMapping("/slots/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(HttpServletRequest httpRequest, @PathVariable UUID slotId) {
        User owner = currentUserResolver.resolve(httpRequest);
        slotService.deleteSlot(owner, slotId);
    }
}
