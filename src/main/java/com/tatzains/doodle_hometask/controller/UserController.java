package com.tatzains.doodle_hometask.controller;

import com.tatzains.doodle_hometask.dto.request.CreateUserRequest;
import com.tatzains.doodle_hometask.dto.response.UserResponse;
import com.tatzains.doodle_hometask.mapper.UserMapper;
import com.tatzains.doodle_hometask.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "User creation. No password/session — see X-User-Id on other endpoints.")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Create a user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Bean validation failure (blank name, invalid email)"),
            @ApiResponse(responseCode = "409", description = "Email already in use")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return UserMapper.toResponse(userService.createUser(request));
    }
}
