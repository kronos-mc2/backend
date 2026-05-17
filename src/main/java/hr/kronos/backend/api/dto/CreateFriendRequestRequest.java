package hr.kronos.backend.api.dto;

public record CreateFriendRequestRequest(String recipientUserId, String chatRoomId) {}
