package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.ChatMessageDto;
import hr.kronos.backend.api.dto.ChatPersonDto;
import hr.kronos.backend.api.dto.ChatRoomDetailDto;
import hr.kronos.backend.api.dto.ChatRoomDto;
import hr.kronos.backend.api.dto.ConversationDto;
import hr.kronos.backend.api.dto.CreateChatRoomRequest;
import hr.kronos.backend.api.dto.CreatePollRequest;
import hr.kronos.backend.api.dto.PollDto;
import hr.kronos.backend.api.dto.SendMessageRequest;
import hr.kronos.backend.api.dto.ShareEventRequest;
import hr.kronos.backend.api.dto.UpdateChatNotificationSettingsRequest;
import hr.kronos.backend.api.dto.UpdateChatRoomRequest;
import hr.kronos.backend.api.dto.VotePollRequest;
import hr.kronos.backend.messages.MessageService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
  private final MessageService messageService;

  public MessageController(MessageService messageService) {
    this.messageService = messageService;
  }

  @GetMapping("/conversations")
  public List<ConversationDto> getConversations() {
    return messageService.getConversations();
  }

  @GetMapping("/people")
  public List<ChatPersonDto> searchPeople(@RequestParam(required = false) String query, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.searchPeople(userId, query);
  }

  @GetMapping("/chat-rooms")
  public List<ChatRoomDto> getChatRooms(@RequestParam(required = false) String query, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.getChatRooms(userId, query);
  }

  @PostMapping("/chat-rooms")
  @ResponseStatus(HttpStatus.CREATED)
  public ChatRoomDto createChatRoom(@RequestBody CreateChatRoomRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.createChatRoom(request, userId);
  }

  @PostMapping("/events/{eventId}/chat-room")
  public ChatRoomDto getOrCreateEventChatRoom(@PathVariable String eventId, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.getOrCreateEventChatRoom(eventId, userId);
  }

  @GetMapping("/chat-rooms/{id}")
  public ChatRoomDetailDto getChatRoom(@PathVariable String id, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.getChatRoom(id, userId);
  }

  @PatchMapping("/chat-rooms/{id}")
  public ChatRoomDto updateChatRoom(
      @PathVariable String id, @RequestBody UpdateChatRoomRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.updateChatRoom(id, request == null ? null : request.adminOnly(), userId);
  }

  @PatchMapping("/chat-rooms/{id}/notification-settings")
  public ChatRoomDto updateChatNotificationSettings(
      @PathVariable String id,
      @RequestBody UpdateChatNotificationSettingsRequest request,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.updateChatNotificationSettings(id, request == null ? null : request.muted(), userId);
  }

  @GetMapping("/chat-rooms/{id}/messages")
  public List<ChatMessageDto> getMessages(@PathVariable String id, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.getMessages(id, userId);
  }

  @PostMapping("/chat-rooms/{id}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public ChatMessageDto sendMessage(
      @PathVariable String id, @RequestBody SendMessageRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.sendTextMessage(id, request.body(), userId);
  }

  @PostMapping("/chat-rooms/{id}/share-event")
  @ResponseStatus(HttpStatus.CREATED)
  public ChatMessageDto shareEvent(
      @PathVariable String id, @RequestBody ShareEventRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.shareEvent(id, request.eventId(), userId);
  }

  @PostMapping("/chat-rooms/{id}/polls")
  @ResponseStatus(HttpStatus.CREATED)
  public ChatMessageDto createPoll(
      @PathVariable String id, @RequestBody CreatePollRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.createPoll(id, request, userId);
  }

  @PostMapping("/polls/{id}/vote")
  public PollDto votePoll(@PathVariable String id, @RequestBody VotePollRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.votePoll(id, request, userId);
  }

  @PostMapping("/conversations/{id}/share-event")
  public ConversationDto shareEventToLegacyConversation(
      @PathVariable String id, @RequestBody ShareEventRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return messageService.shareEventToLegacyConversation(id, request.eventId(), userId);
  }
}
