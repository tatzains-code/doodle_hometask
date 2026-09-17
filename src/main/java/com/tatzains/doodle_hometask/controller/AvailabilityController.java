package com.tatzains.doodle_hometask.controller;

import com.tatzains.doodle_hometask.dto.response.AvailabilitySlotResponse;
import com.tatzains.doodle_hometask.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Availability", description = "Public, restricted view of another user's free/busy slots")
@RestController
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @Operation(summary = "List another user's availability in a time range",
            description = "Restricted projection: timing and FREE/BUSY status only — never meeting "
                    + "title, description, or participants. Does not require X-User-Id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slots returned"),
            @ApiResponse(responseCode = "400", description = "'to' not after 'from', or range exceeds the configured maximum"),
            @ApiResponse(responseCode = "404", description = "ownerId does not exist")
    })
    @GetMapping("/availability")
    public PagedModel<AvailabilitySlotResponse> getAvailability(
            @Parameter(description = "User whose availability is being queried") @RequestParam UUID ownerId,
            @Parameter(description = "Range start, defaults to now") @RequestParam(required = false) Instant from,
            @Parameter(description = "Range end, defaults to a configured default range after 'from'") @RequestParam(required = false) Instant to,
            @Parameter(description = "Only return slots at least this many minutes long") @RequestParam(required = false) Integer duration,
            @PageableDefault(size = 50, sort = "start") Pageable pageable) {
        return new PagedModel<>(availabilityService.getAvailability(ownerId, from, to, duration, pageable));
    }
}
