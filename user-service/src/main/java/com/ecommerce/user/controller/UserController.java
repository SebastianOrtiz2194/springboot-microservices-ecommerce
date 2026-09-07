package com.ecommerce.user.controller;

import com.ecommerce.user.domain.User;
import com.ecommerce.user.dto.CreateUserRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.exception.UserNotFoundException;
import com.ecommerce.user.mapper.UserMapper;
import com.ecommerce.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for user management endpoints. */
@Tag(name = "users", description = "User management (ADMIN for writes)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    /**
     * Creates a new user.
     *
     * @param request the validated user payload
     * @return the created user
     */
    @Operation(summary = "Create a user", description = "Requires ADMIN role")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userMapper.toEntity(request);
        User saved = userService.createUser(user);
        return userMapper.toResponse(saved);
    }

    /**
     * Retrieves a user by ID.
     *
     * @param id the user identifier
     * @return the user
     * @throws UserNotFoundException if the user does not exist
     */
    @Operation(summary = "Get a user by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "400", description = "Invalid ID"),
        @ApiResponse(
                responseCode = "404",
                description = "User not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public UserResponse getUser(@Positive @PathVariable Long id) {
        User user = userService.getUserById(id);
        return userMapper.toResponse(user);
    }

    /**
     * Lists all registered users.
     *
     * @return list of all users
     */
    @Operation(summary = "List all users")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "User list")})
    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers().stream().map(userMapper::toResponse).toList();
    }
}
