package hr.kronos.backend.social;

import hr.kronos.backend.api.dto.CreateFriendRequestRequest;
import hr.kronos.backend.api.dto.FriendDto;
import hr.kronos.backend.api.dto.FriendRequestDto;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.auth.persistence.AuthMapper;
import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.messages.MessageService;
import hr.kronos.backend.messages.persistence.MessageMapper;
import hr.kronos.backend.social.persistence.FriendRequestRow;
import hr.kronos.backend.social.persistence.FriendRow;
import hr.kronos.backend.social.persistence.SocialMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SocialService {
  private static final String VISIBILITY_FRIENDS = "friends";

  private final SocialMapper socialMapper;
  private final AuthMapper authMapper;
  private final EventMapper eventMapper;
  private final MessageMapper messageMapper;
  private final MessageService messageService;

  public SocialService(
      SocialMapper socialMapper,
      AuthMapper authMapper,
      EventMapper eventMapper,
      MessageMapper messageMapper,
      MessageService messageService) {
    this.socialMapper = socialMapper;
    this.authMapper = authMapper;
    this.eventMapper = eventMapper;
    this.messageMapper = messageMapper;
    this.messageService = messageService;
  }

  public List<FriendDto> getFriends() {
    return socialMapper.findFriends().stream()
        .map(this::toFriendDto)
        .toList();
  }

  public List<FriendDto> getFriends(String userId) {
    return socialMapper.findFriendsForUser(userId).stream()
        .map(this::toFriendDto)
        .toList();
  }

  public List<FriendDto> getShareableFriendsForEvent(String eventId, String userId) {
    EventRow event = eventMapper.findAccessibleById(eventId, userId);
    if (event == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
    }

    if (!VISIBILITY_FRIENDS.equals(event.getVisibility())) {
      return getFriends(userId);
    }

    String creatorUserId = trimToNull(event.getCreatorUserId());
    if (creatorUserId == null) {
      return List.of();
    }
    if (creatorUserId.equals(userId)) {
      return getFriends(userId);
    }

    return socialMapper.findShareableFriendsForEvent(userId, creatorUserId).stream()
        .map(this::toFriendDto)
        .toList();
  }

  public FriendRequestDto createFriendRequest(CreateFriendRequestRequest request, String requesterUserId) {
    if (request == null || trimToNull(request.recipientUserId()) == null || trimToNull(request.chatRoomId()) == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipientUserId and chatRoomId are required.");
    }

    String recipientUserId = request.recipientUserId().trim();
    if (recipientUserId.equals(requesterUserId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot friend yourself.");
    }
    if (authMapper.findById(recipientUserId) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
    }
    var room = messageMapper.findRoomForUser(request.chatRoomId(), requesterUserId);
    if (room == null || !"direct".equals(room.getRoomType()) || !recipientUserId.equals(room.getDirectUserId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found.");
    }

    FriendRequestRow existing = socialMapper.findFriendRequestBetween(requesterUserId, recipientUserId);
    if (existing != null) {
      return toDto(existing);
    }

    String requestId = "friend-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
    socialMapper.insertFriendRequest(requestId, requesterUserId, recipientUserId, request.chatRoomId().trim());
    messageService.sendFriendRequestMessage(request.chatRoomId().trim(), requestId, requesterUserId);
    return toDto(socialMapper.findFriendRequest(requestId));
  }

  public FriendRequestDto respondToFriendRequest(String requestId, String status, String userId) {
    FriendRequestRow request = socialMapper.findFriendRequest(requestId);
    if (request == null || !userId.equals(request.getRecipientUserId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend request not found.");
    }
    if (!"pending".equals(request.getStatus())) {
      return toDto(request);
    }

    String normalizedStatus = trimToNull(status);
    if (!"accepted".equals(normalizedStatus) && !"rejected".equals(normalizedStatus)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be accepted or rejected.");
    }
    socialMapper.updateFriendRequestStatus(requestId, normalizedStatus);
    FriendRequestRow updated = socialMapper.findFriendRequest(requestId);
    if (updated != null) {
      messageService.publishFriendRequestUpdated(updated.getChatRoomId());
    }
    return toDto(updated);
  }

  private FriendRequestDto toDto(FriendRequestRow row) {
    if (row == null) {
      return null;
    }

    return new FriendRequestDto(
        row.getId(),
        row.getRequesterUserId(),
        row.getRequesterName(),
        row.getRecipientUserId(),
        row.getRecipientName(),
        row.getStatus(),
        row.getChatRoomId(),
        row.getCreatedAt() == null ? null : row.getCreatedAt().toString(),
        row.getRespondedAt() == null ? null : row.getRespondedAt().toString());
  }

  private FriendDto toFriendDto(FriendRow row) {
    return new FriendDto(
        row.getId(),
        row.getName(),
        row.getAvatarUrl(),
        new LocalizedTextDto(row.getStatusHr(), row.getStatusEn()));
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
