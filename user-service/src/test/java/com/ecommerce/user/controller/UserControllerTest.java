package com.ecommerce.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.user.config.JwtAuthFilter;
import com.ecommerce.user.config.SecurityConfig;
import com.ecommerce.user.domain.User;
import com.ecommerce.user.dto.CreateUserRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.mapper.UserMapper;
import com.ecommerce.user.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Web-layer slice tests for {@link UserController} with security and validation paths. */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private UserService userService;

    @MockBean private UserMapper userMapper;

    @MockBean private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void passThroughAuthFilter() throws Exception {
        // The real filter parses JWTs; in web slices we let security rely on @WithMockUser
        doAnswer(
                        inv -> {
                            inv.<FilterChain>getArgument(2)
                                    .doFilter(inv.getArgument(0), inv.getArgument(1));
                            return null;
                        })
                .when(jwtAuthFilter)
                .doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_returnsCreatedWhenAdmin() throws Exception {
        when(userMapper.toEntity(any(CreateUserRequest.class)))
                .thenReturn(new User("Alice", "alice@example.com", "hashed", "ADMIN"));
        User saved = new User("Alice", "alice@example.com", "hashed", "ADMIN");
        saved.setId(1L);
        when(userService.createUser(any(User.class))).thenReturn(saved);
        when(userMapper.toResponse(saved))
                .thenReturn(new UserResponse(1L, "Alice", "alice@example.com", "ADMIN"));

        mockMvc.perform(
                        post("/api/users")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"Alice","email":"alice@example.com","password":"pw","role":"ADMIN"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_returnsBadRequestWhenValidationFails() throws Exception {
        mockMvc.perform(
                        post("/api/users")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"","email":"not-an-email","password":"","role":""}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createUser_returnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(
                        post("/api/users")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"Alice","email":"alice@example.com","password":"pw","role":"USER"}
                                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_returnsForbiddenWhenAnonymous() throws Exception {
        mockMvc.perform(
                        post("/api/users")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"Alice","email":"alice@example.com","password":"pw","role":"USER"}
                                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getUser_returnsUserForAuthenticatedRole() throws Exception {
        User user = new User("Alice", "alice@example.com", "hashed", "USER");
        user.setId(1L);
        when(userService.getUserById(1L)).thenReturn(user);
        when(userMapper.toResponse(user))
                .thenReturn(new UserResponse(1L, "Alice", "alice@example.com", "USER"));

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getUser_returnsBadRequestForNonPositiveId() throws Exception {
        mockMvc.perform(get("/api/users/0")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllUsers_returnsList() throws Exception {
        User user = new User("Alice", "alice@example.com", "hashed", "USER");
        user.setId(1L);
        when(userService.getAllUsers()).thenReturn(java.util.List.of(user));
        when(userMapper.toResponse(user))
                .thenReturn(new UserResponse(1L, "Alice", "alice@example.com", "USER"));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }
}
