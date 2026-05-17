package hr.kronos.backend.api.dto;

public record ChatMessageDto(
    String id,
    String roomId,
    String type,
    String body,
    String senderUserId,
    String senderName,
    String senderAvatarUrl,
    String createdAt,
    String timeLabel,
    boolean mine,
    EventSharePreviewDto event,
    PollDto poll,
    FriendRequestDto friendRequest) {}
