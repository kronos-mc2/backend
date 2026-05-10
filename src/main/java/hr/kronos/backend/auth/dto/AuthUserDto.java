package hr.kronos.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthUserDto(String name, String email, String bio, String avatarUrl) {}
