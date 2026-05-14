package hr.kronos.backend.api.dto;

import java.util.List;

public record ChatRoomDto(
    String id,
    String type,
    String title,
    String avatarUrl,
    String directUserId,
    String subtitle,
    String lastMessage,
    String lastMessageAt,
    String timeLabel,
    int unreadCount,
    int memberCount,
    String myRole,
    boolean adminOnly,
    String eventId,
    List<ChatMemberDto> members) {}
