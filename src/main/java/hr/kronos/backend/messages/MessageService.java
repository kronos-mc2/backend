package hr.kronos.backend.messages;

import hr.kronos.backend.api.dto.ChatMemberDto;
import hr.kronos.backend.api.dto.ChatMessageDto;
import hr.kronos.backend.api.dto.ChatPersonDto;
import hr.kronos.backend.api.dto.ChatRoomDetailDto;
import hr.kronos.backend.api.dto.ChatRoomDto;
import hr.kronos.backend.api.dto.ConversationDto;
import hr.kronos.backend.api.dto.CreateChatRoomRequest;
import hr.kronos.backend.api.dto.CreatePollRequest;
import hr.kronos.backend.api.dto.EventSharePreviewDto;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.api.dto.PollDto;
import hr.kronos.backend.api.dto.PollOptionDto;
import hr.kronos.backend.api.dto.VotePollRequest;
import hr.kronos.backend.auth.persistence.AuthMapper;
import hr.kronos.backend.auth.persistence.UserRow;
import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventMediaRow;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.messages.persistence.ChatMemberRow;
import hr.kronos.backend.messages.persistence.ChatMessageRow;
import hr.kronos.backend.messages.persistence.ChatPersonRow;
import hr.kronos.backend.messages.persistence.ChatRoomRow;
import hr.kronos.backend.messages.persistence.ConversationRow;
import hr.kronos.backend.messages.persistence.MessageMapper;
import hr.kronos.backend.messages.persistence.PollOptionRow;
import hr.kronos.backend.messages.persistence.PollRow;
import hr.kronos.backend.messages.realtime.ChatRealtimeEvent;
import hr.kronos.backend.messages.realtime.ChatRealtimeService;
import hr.kronos.backend.notifications.NotificationService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MessageService {
  private static final int MAX_MESSAGE_LENGTH = 4000;
  private static final int MAX_POLL_OPTIONS = 8;
  private static final int MIN_PEOPLE_SEARCH_LENGTH = 2;
  private static final String ROOM_TYPE_DIRECT = "direct";
  private static final String ROOM_TYPE_EVENT = "event";
  private static final String ROOM_TYPE_GROUP = "group";
  private static final String MEMBER_ROLE_MEMBER = "member";
  private static final String MEMBER_ROLE_OWNER = "owner";
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  private final MessageMapper messageMapper;
  private final EventMapper eventMapper;
  private final AuthMapper authMapper;
  private final ChatRealtimeService chatRealtimeService;
  private final NotificationService notificationService;

  public MessageService(
      MessageMapper messageMapper,
      EventMapper eventMapper,
      AuthMapper authMapper,
      ChatRealtimeService chatRealtimeService,
      NotificationService notificationService) {
    this.messageMapper = messageMapper;
    this.eventMapper = eventMapper;
    this.authMapper = authMapper;
    this.chatRealtimeService = chatRealtimeService;
    this.notificationService = notificationService;
  }

  public List<ConversationDto> getConversations() {
    return messageMapper.findConversations().stream().map(this::toDto).toList();
  }

  public List<ChatRoomDto> getChatRooms(String userId, String query) {
    return messageMapper.findRoomsForUser(userId, trimToNull(query)).stream()
        .map(room -> toRoomDto(room, null))
        .toList();
  }

  public ChatRoomDetailDto getChatRoom(String roomId, String userId) {
    ChatRoomRow room = requireRoom(roomId, userId);
    List<ChatMemberDto> members = messageMapper.findMembersForRoom(roomId).stream().map(this::toMemberDto).toList();
    List<ChatMessageDto> messages = messageMapper.findMessagesForRoom(roomId, userId).stream()
        .map(message -> toMessageDto(message, userId))
        .toList();

    if (!messages.isEmpty()) {
      messageMapper.markRoomRead(roomId, userId, messages.get(messages.size() - 1).id());
    }

    return new ChatRoomDetailDto(toRoomDto(room, members), messages);
  }

  public List<ChatMessageDto> getMessages(String roomId, String userId) {
    requireRoom(roomId, userId);
    List<ChatMessageDto> messages = messageMapper.findMessagesForRoom(roomId, userId).stream()
        .map(message -> toMessageDto(message, userId))
        .toList();

    if (!messages.isEmpty()) {
      messageMapper.markRoomRead(roomId, userId, messages.get(messages.size() - 1).id());
    }

    return messages;
  }

  public List<ChatPersonDto> searchPeople(String userId, String query) {
    String normalizedQuery = trimToNull(query);
    if (normalizedQuery == null || normalizedQuery.length() < MIN_PEOPLE_SEARCH_LENGTH) {
      return List.of();
    }

    return messageMapper.searchPeople(userId, normalizedQuery).stream().map(this::toPersonDto).toList();
  }

  public ChatRoomDto createChatRoom(CreateChatRoomRequest request, String userId) {
    String type = normalizeRoomType(request.type());
    if (ROOM_TYPE_EVENT.equals(type)) {
      if (request.eventId() == null || request.eventId().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId is required.");
      }
      return getOrCreateEventChatRoom(request.eventId(), userId);
    }

    if (ROOM_TYPE_DIRECT.equals(type)) {
      return createDirectChatRoom(request, userId);
    }

    return createGroupChatRoom(request, userId);
  }

  private ChatRoomDto createDirectChatRoom(CreateChatRoomRequest request, String userId) {
    String memberUserId = trimToNull(request.memberUserId());
    if (memberUserId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memberUserId is required.");
    }
    if (memberUserId.equals(userId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create a direct chat with yourself.");
    }

    UserRow targetUser = authMapper.findById(memberUserId);
    if (targetUser == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
    }

    ChatRoomRow existing = messageMapper.findDirectRoomForUsers(userId, memberUserId);
    if (existing != null) {
      return toRoomDto(existing, messageMapper.findMembersForRoom(existing.getId()).stream().map(this::toMemberDto).toList());
    }

    ChatRoomRow room = newRoom(ROOM_TYPE_DIRECT, null, null, false, userId);
    messageMapper.insertRoom(room);
    messageMapper.insertMember(room.getId(), userId, MEMBER_ROLE_OWNER);
    messageMapper.insertMember(room.getId(), memberUserId, MEMBER_ROLE_MEMBER);
    ChatRoomDto roomDto = getChatRoom(room.getId(), userId).room();
    publishRoomUpdated(room.getId());
    return roomDto;
  }

  private ChatRoomDto createGroupChatRoom(CreateChatRoomRequest request, String userId) {
    String title = trimToNull(request.title());
    if (title == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required.");
    }

    ChatRoomRow room = newRoom(ROOM_TYPE_GROUP, title, null, false, userId);
    messageMapper.insertRoom(room);
    messageMapper.insertMember(room.getId(), userId, MEMBER_ROLE_OWNER);
    for (String memberUserId : uniqueMemberIds(request.memberUserIds(), userId)) {
      if (authMapper.findById(memberUserId) != null) {
        messageMapper.insertMember(room.getId(), memberUserId, MEMBER_ROLE_MEMBER);
      }
    }
    ChatRoomDto roomDto = getChatRoom(room.getId(), userId).room();
    publishRoomUpdated(room.getId());
    return roomDto;
  }

  public ChatRoomDto getOrCreateEventChatRoom(String eventId, String userId) {
    EventRow event = eventMapper.findAccessibleById(eventId, userId);
    if (event == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
    }

    ChatRoomRow existing = messageMapper.findEventRoomForUser(eventId, userId);
    if (existing != null) {
      ensureMember(
          existing.getId(),
          userId,
          event.getCreatorUserId() != null && event.getCreatorUserId().equals(userId)
              ? MEMBER_ROLE_OWNER
              : MEMBER_ROLE_MEMBER);
      ChatRoomRow refreshed = requireRoom(existing.getId(), userId);
      return toRoomDto(refreshed, messageMapper.findMembersForRoom(refreshed.getId()).stream().map(this::toMemberDto).toList());
    }

    boolean creatorIsCurrentUser = event.getCreatorUserId() != null && event.getCreatorUserId().equals(userId);
    ChatRoomRow room = newRoom(ROOM_TYPE_EVENT, event.getTitleHr(), eventId, false, userId);
    messageMapper.insertRoom(room);
    messageMapper.insertMember(
        room.getId(), userId, creatorIsCurrentUser ? MEMBER_ROLE_OWNER : MEMBER_ROLE_MEMBER);
    if (event.getCreatorUserId() != null && !event.getCreatorUserId().equals(userId)) {
      messageMapper.insertMember(room.getId(), event.getCreatorUserId(), MEMBER_ROLE_OWNER);
    }
    ChatRoomDto roomDto = getChatRoom(room.getId(), userId).room();
    publishRoomUpdated(room.getId());
    return roomDto;
  }

  public void leaveEventChatRoom(String eventId, String userId) {
    String roomId = messageMapper.findEventRoomId(eventId);
    if (roomId == null) {
      return;
    }

    messageMapper.deleteRoomRead(roomId, userId);
    int deleted = messageMapper.deleteMember(roomId, userId);
    if (deleted > 0) {
      publishRoomUpdated(roomId);
    }
  }

  public ChatRoomDto updateChatRoom(String roomId, Boolean adminOnly, String userId) {
    ChatRoomRow room = requireRoom(roomId, userId);
    requireAdmin(room, userId);
    if (adminOnly != null && !ROOM_TYPE_DIRECT.equals(room.getRoomType())) {
      messageMapper.updateRoomAdminOnly(roomId, adminOnly);
    }
    ChatRoomDto roomDto = getChatRoom(roomId, userId).room();
    publishRoomUpdated(roomId);
    return roomDto;
  }

  public ChatRoomDto updateChatNotificationSettings(String roomId, Boolean muted, String userId) {
    requireRoom(roomId, userId);
    notificationService.setChatMuted(roomId, Boolean.TRUE.equals(muted), userId);
    ChatRoomRow refreshed = requireRoom(roomId, userId);
    ChatRoomDto roomDto = toRoomDto(refreshed, messageMapper.findMembersForRoom(roomId).stream().map(this::toMemberDto).toList());
    publishRoomUpdated(roomId);
    return roomDto;
  }

  public ChatMessageDto sendTextMessage(String roomId, String body, String userId) {
    ChatRoomRow room = requireRoom(roomId, userId);
    requireCanWrite(room, userId);

    String normalizedBody = trimToNull(body);
    if (normalizedBody == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required.");
    }
    if (normalizedBody.length() > MAX_MESSAGE_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is too long.");
    }

    ChatMessageRow message = newMessage(roomId, userId, "text", normalizedBody, null, null);
    messageMapper.insertMessage(message);
    messageMapper.touchRoom(roomId);
    ChatMessageDto messageDto = toMessageDto(messageMapper.findMessageById(message.getId(), userId), userId);
    publishMessageCreated(roomId, messageDto.id());
    notificationService.sendChatMessagePush(roomId, messageDto, userId);
    return messageDto;
  }

  public ChatMessageDto shareEvent(String roomId, String eventId, String userId) {
    if (eventId == null || eventId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId is required.");
    }

    ChatRoomRow room = requireRoom(roomId, userId);
    requireCanWrite(room, userId);
    EventRow event = eventMapper.findAccessibleById(eventId, userId);
    if (event == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
    }

    ChatMessageRow message = newMessage(roomId, userId, "event_share", null, eventId, null);
    messageMapper.insertMessage(message);
    messageMapper.touchRoom(roomId);
    updateLegacyConversationPreview(roomId, event);
    ChatMessageDto messageDto = toMessageDto(messageMapper.findMessageById(message.getId(), userId), userId);
    publishMessageCreated(roomId, messageDto.id());
    notificationService.sendChatMessagePush(roomId, messageDto, userId);
    return messageDto;
  }

  public ConversationDto shareEventToLegacyConversation(String conversationId, String eventId, String userId) {
    shareEvent(conversationId, eventId, userId);
    ConversationRow updatedConversation = messageMapper.findConversationById(conversationId);
    if (updatedConversation == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found.");
    }
    return toDto(updatedConversation);
  }

  public ChatMessageDto createPoll(String roomId, CreatePollRequest request, String userId) {
    ChatRoomRow room = requireRoom(roomId, userId);
    requireCanWrite(room, userId);

    String question = trimToNull(request.question());
    if (question == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is required.");
    }

    List<String> options = normalizePollOptions(request.options());
    PollRow poll = new PollRow();
    poll.setId(prefixedId("poll"));
    poll.setRoomId(roomId);
    poll.setQuestion(question);
    poll.setAllowMultiple(Boolean.TRUE.equals(request.allowMultiple()));
    poll.setCreatedByUserId(userId);
    messageMapper.insertPoll(poll);

    for (int index = 0; index < options.size(); index++) {
      PollOptionRow option = new PollOptionRow();
      option.setId(prefixedId("poll-option"));
      option.setPollId(poll.getId());
      option.setText(options.get(index));
      option.setSortOrder(index);
      messageMapper.insertPollOption(option);
    }

    ChatMessageRow message = newMessage(roomId, userId, "poll", question, null, poll.getId());
    messageMapper.insertMessage(message);
    messageMapper.touchRoom(roomId);
    ChatMessageDto messageDto = toMessageDto(messageMapper.findMessageById(message.getId(), userId), userId);
    publishMessageCreated(roomId, messageDto.id());
    notificationService.sendChatMessagePush(roomId, messageDto, userId);
    return messageDto;
  }

  public PollDto votePoll(String pollId, VotePollRequest request, String userId) {
    PollRow poll = messageMapper.findPollById(pollId);
    if (poll == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found.");
    }
    requireRoom(poll.getRoomId(), userId);

    List<String> optionIds = normalizeVoteOptions(request.optionIds());
    Set<String> validOptionIds = new LinkedHashSet<>(
        messageMapper.findPollOptions(pollId, userId).stream().map(PollOptionRow::getId).toList());
    for (String optionId : optionIds) {
      if (!validOptionIds.contains(optionId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid poll option.");
      }
    }

    if (!Boolean.TRUE.equals(poll.getAllowMultiple()) && optionIds.size() > 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Poll accepts one option.");
    }

    messageMapper.deletePollVotesForUser(pollId, userId);
    for (String optionId : optionIds) {
      messageMapper.insertPollVote(pollId, optionId, userId);
    }

    PollDto pollDto = toPollDto(poll, userId);
    chatRealtimeService.publishToRoom(
        poll.getRoomId(),
        new ChatRealtimeEvent(ChatRealtimeService.POLL_UPDATED, poll.getRoomId(), Map.of("pollId", pollId)));
    return pollDto;
  }

  private void publishMessageCreated(String roomId, String messageId) {
    chatRealtimeService.publishToRoom(
        roomId,
        new ChatRealtimeEvent(ChatRealtimeService.MESSAGE_CREATED, roomId, Map.of("messageId", messageId)));
  }

  private void publishRoomUpdated(String roomId) {
    chatRealtimeService.publishToRoom(
        roomId,
        new ChatRealtimeEvent(ChatRealtimeService.ROOM_UPDATED, roomId, Map.of("roomId", roomId)));
  }

  private ChatRoomRow requireRoom(String roomId, String userId) {
    ChatRoomRow room = messageMapper.findRoomForUser(roomId, userId);
    if (room == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found.");
    }
    return room;
  }

  private void requireCanWrite(ChatRoomRow room, String userId) {
    if (ROOM_TYPE_DIRECT.equals(room.getRoomType())) {
      return;
    }
    if (!Boolean.TRUE.equals(room.getAdminOnly())) {
      return;
    }
    requireAdmin(room, userId);
  }

  private void requireAdmin(ChatRoomRow room, String userId) {
    ChatMemberRow member = messageMapper.findMember(room.getId(), userId);
    if (member == null || (!MEMBER_ROLE_OWNER.equals(member.getRole()) && !"admin".equals(member.getRole()))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can do this.");
    }
  }

  private void ensureMember(String roomId, String userId, String role) {
    if (messageMapper.findMember(roomId, userId) == null) {
      messageMapper.insertMember(roomId, userId, role);
    }
  }

  private ChatRoomDto toRoomDto(ChatRoomRow row, List<ChatMemberDto> members) {
    return new ChatRoomDto(
        row.getId(),
        row.getRoomType(),
        firstNonBlank(row.getDisplayTitle(), row.getTitle(), "Razgovor"),
        row.getAvatarUrl(),
        row.getDirectUserId(),
        row.getSubtitle(),
        row.getLastMessage(),
        timestamp(row.getLastMessageAt()),
        row.getTimeLabel(),
        row.getUnreadCount() == null ? 0 : row.getUnreadCount(),
        row.getMemberCount() == null ? 0 : row.getMemberCount(),
        row.getMyRole(),
        Boolean.TRUE.equals(row.getAdminOnly()),
        Boolean.TRUE.equals(row.getMutedByMe()),
        row.getEventId(),
        members);
  }

  private ChatMessageDto toMessageDto(ChatMessageRow row, String userId) {
    return new ChatMessageDto(
        row.getId(),
        row.getRoomId(),
        row.getMessageType(),
        row.getBody(),
        row.getSenderUserId(),
        row.getSenderName(),
        row.getSenderAvatarUrl(),
        timestamp(row.getCreatedAt()),
        row.getCreatedAt() == null ? null : TIME_FORMATTER.format(row.getCreatedAt()),
        row.getSenderUserId() != null && row.getSenderUserId().equals(userId),
        row.getEventId() == null ? null : toEventSharePreview(row.getEventId(), userId),
        row.getPollId() == null ? null : toPollDto(messageMapper.findPollById(row.getPollId()), userId));
  }

  private PollDto toPollDto(PollRow poll, String userId) {
    if (poll == null) {
      return null;
    }

    List<PollOptionDto> options = messageMapper.findPollOptions(poll.getId(), userId).stream().map(this::toPollOptionDto).toList();
    List<String> myOptionIds = options.stream().filter(PollOptionDto::votedByMe).map(PollOptionDto::id).toList();
    return new PollDto(
        poll.getId(),
        poll.getQuestion(),
        Boolean.TRUE.equals(poll.getAllowMultiple()),
        messageMapper.countPollVotes(poll.getId()),
        poll.getClosesAt() != null && poll.getClosesAt().isBefore(OffsetDateTime.now()),
        myOptionIds,
        options);
  }

  private EventSharePreviewDto toEventSharePreview(String eventId, String userId) {
    EventRow event = eventMapper.findAccessibleById(eventId, userId);
    if (event == null) {
      return null;
    }

    String coverUrl = eventMapper.findMediaByEventId(eventId).stream()
        .findFirst()
        .map(EventMediaRow::getThumbnailUrl)
        .orElseGet(() -> eventMapper.findMediaByEventId(eventId).stream().findFirst().map(EventMediaRow::getUrl).orElse(null));

    return new EventSharePreviewDto(
        event.getId(),
        new LocalizedTextDto(event.getTitleHr(), event.getTitleEn()),
        new LocalizedTextDto(event.getWhereHr(), event.getWhereEn()),
        new LocalizedTextDto(event.getAboutHr(), event.getAboutEn()),
        timestamp(event.getStartAt() == null ? event.getWhenIso() : event.getStartAt()),
        coverUrl);
  }

  private PollOptionDto toPollOptionDto(PollOptionRow row) {
    return new PollOptionDto(
        row.getId(),
        row.getText(),
        row.getVoteCount() == null ? 0 : row.getVoteCount(),
        Boolean.TRUE.equals(row.getVotedByMe()));
  }

  private ChatMemberDto toMemberDto(ChatMemberRow row) {
    return new ChatMemberDto(row.getUserId(), row.getFullName(), row.getAvatarUrl(), row.getRole());
  }

  private ChatPersonDto toPersonDto(ChatPersonRow row) {
    return new ChatPersonDto(row.getId(), row.getFullName(), row.getEmail(), row.getAvatarUrl());
  }

  private ConversationDto toDto(ConversationRow row) {
    return new ConversationDto(
        row.getId(),
        row.getContact(),
        new LocalizedTextDto(row.getLastMessageHr(), row.getLastMessageEn()),
        row.getTimeLabel());
  }

  private ChatRoomRow newRoom(String roomType, String title, String eventId, boolean adminOnly, String userId) {
    ChatRoomRow row = new ChatRoomRow();
    row.setId(prefixedId("chat"));
    row.setRoomType(roomType);
    row.setTitle(title);
    row.setEventId(eventId);
    row.setAdminOnly(adminOnly);
    row.setCreatedByUserId(userId);
    return row;
  }

  private ChatMessageRow newMessage(String roomId, String userId, String type, String body, String eventId, String pollId) {
    ChatMessageRow row = new ChatMessageRow();
    row.setId(prefixedId("msg"));
    row.setRoomId(roomId);
    row.setSenderUserId(userId);
    row.setMessageType(type);
    row.setBody(body);
    row.setEventId(eventId);
    row.setPollId(pollId);
    return row;
  }

  private List<String> normalizePollOptions(List<String> rawOptions) {
    if (rawOptions == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options are required.");
    }

    List<String> options = rawOptions.stream().map(this::trimToNull).filter(Objects::nonNull).distinct().toList();
    if (options.size() < 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Poll requires at least two options.");
    }
    if (options.size() > MAX_POLL_OPTIONS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Poll accepts up to " + MAX_POLL_OPTIONS + " options.");
    }
    return options;
  }

  private List<String> normalizeVoteOptions(List<String> rawOptionIds) {
    if (rawOptionIds == null || rawOptionIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "optionIds are required.");
    }
    return rawOptionIds.stream().map(this::trimToNull).filter(Objects::nonNull).distinct().toList();
  }

  private List<String> uniqueMemberIds(List<String> memberUserIds, String currentUserId) {
    if (memberUserIds == null) {
      return List.of();
    }

    Set<String> unique = new LinkedHashSet<>();
    for (String memberUserId : memberUserIds) {
      String normalized = trimToNull(memberUserId);
      if (normalized != null && !normalized.equals(currentUserId)) {
        unique.add(normalized);
      }
    }
    return new ArrayList<>(unique);
  }

  private String normalizeRoomType(String type) {
    String normalized = trimToNull(type);
    if (normalized == null) {
      return ROOM_TYPE_DIRECT;
    }
    if (!ROOM_TYPE_DIRECT.equals(normalized) && !ROOM_TYPE_GROUP.equals(normalized) && !ROOM_TYPE_EVENT.equals(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported room type.");
    }
    return normalized;
  }

  private void updateLegacyConversationPreview(String conversationId, EventRow event) {
    messageMapper.updateConversationSharePreview(
        conversationId,
        "Podijeljen event: " + event.getTitleHr(),
        "Shared event: " + event.getTitleEn());
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private String firstNonBlank(String first, String second, String fallback) {
    String normalizedFirst = trimToNull(first);
    if (normalizedFirst != null) {
      return normalizedFirst;
    }
    String normalizedSecond = trimToNull(second);
    return normalizedSecond == null ? fallback : normalizedSecond;
  }

  private String timestamp(OffsetDateTime value) {
    return value == null ? null : value.toString();
  }

  private String prefixedId(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
  }
}
