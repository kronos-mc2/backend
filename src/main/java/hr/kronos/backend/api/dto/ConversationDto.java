package hr.kronos.backend.api.dto;

public record ConversationDto(String id, String contact, LocalizedTextDto lastMessage, String timeLabel) {}
