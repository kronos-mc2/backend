package hr.kronos.backend.messages;

import hr.kronos.backend.api.dto.ConversationDto;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.messages.persistence.MessageMapper;
import hr.kronos.backend.messages.persistence.ConversationRow;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MessageService {
  private final MessageMapper messageMapper;
  private final EventMapper eventMapper;

  public MessageService(MessageMapper messageMapper, EventMapper eventMapper) {
    this.messageMapper = messageMapper;
    this.eventMapper = eventMapper;
  }

  public List<ConversationDto> getConversations() {
    return messageMapper.findConversations().stream()
        .map(this::toDto)
        .toList();
  }

  public ConversationDto shareEvent(String conversationId, String eventId, String userId) {
    if (eventId == null || eventId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId is required.");
    }

    ConversationRow conversation = messageMapper.findConversationById(conversationId);
    if (conversation == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found.");
    }

    EventRow event = eventMapper.findAccessibleById(eventId, userId);
    if (event == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
    }

    messageMapper.updateConversationSharePreview(
        conversationId,
        "Podijeljen event: " + event.getTitleHr(),
        "Shared event: " + event.getTitleEn());

    ConversationRow updatedConversation = messageMapper.findConversationById(conversationId);
    if (updatedConversation == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found.");
    }

    return toDto(updatedConversation);
  }

  private ConversationDto toDto(ConversationRow row) {
    return new ConversationDto(
        row.getId(),
        row.getContact(),
        new LocalizedTextDto(row.getLastMessageHr(), row.getLastMessageEn()),
        row.getTimeLabel());
  }
}
