package com.ecommerce.user.mapper;

import com.ecommerce.user.domain.User;
import com.ecommerce.user.dto.CreateUserRequest;
import com.ecommerce.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between {@link User} entity and its DTOs.
 * MapStruct generates the implementation at compile time.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    @Mapping(target = "id", ignore = true)
    User toEntity(CreateUserRequest request);
}
