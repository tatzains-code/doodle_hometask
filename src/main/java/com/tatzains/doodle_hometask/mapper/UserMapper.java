package com.tatzains.doodle_hometask.mapper;

import com.tatzains.doodle_hometask.domain.User;
import com.tatzains.doodle_hometask.dto.response.UserResponse;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
