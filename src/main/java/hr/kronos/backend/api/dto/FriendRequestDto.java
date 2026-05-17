package hr.kronos.backend.api.dto;

public record FriendRequestDto(
    String id,
    String requesterUserId,
    String requesterName,
    String recipientUserId,
    String recipientName,
    String status,
    String chatRoomId,
    String createdAt,
    String respondedAt) {}
