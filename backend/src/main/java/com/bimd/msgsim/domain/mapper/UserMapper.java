package com.bimd.msgsim.domain.mapper;

import com.bimd.msgsim.domain.dto.UserResponse;
import com.bimd.msgsim.domain.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
