package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.ConversationDto;
import hr.kronos.backend.api.dto.ShareEventRequest;
import hr.kronos.backend.auth.AuthPrincipal;
import hr.kronos.backend.messages.MessageService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

  @PostMapping("/conversations/{id}/share-event")
  public ConversationDto shareEvent(
      @PathVariable String id, @RequestBody ShareEventRequest request, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return messageService.shareEvent(id, request.eventId(), principal.userId());
  }
}
