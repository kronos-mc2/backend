package hr.kronos.backend.api.dto;

import java.util.List;

public record ChatRoomDetailDto(ChatRoomDto room, List<ChatMessageDto> messages) {}
