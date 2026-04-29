package hr.kronos.backend.api.dto;

import java.util.List;

public record CreateChatRoomRequest(String type, String title, String memberUserId, String eventId, List<String> memberUserIds) {}
