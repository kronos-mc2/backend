package hr.kronos.backend.api.dto;

public record PollOptionDto(String id, String text, int voteCount, boolean votedByMe) {}
