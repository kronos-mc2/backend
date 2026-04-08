package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.ConversationDto;
import hr.kronos.backend.messages.MessageService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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
}
