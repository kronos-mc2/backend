package hr.kronos.backend.messages;

import hr.kronos.backend.api.dto.ConversationDto;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.messages.persistence.MessageMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MessageService {
  private final MessageMapper messageMapper;

  public MessageService(MessageMapper messageMapper) {
    this.messageMapper = messageMapper;
  }

  public List<ConversationDto> getConversations() {
    return messageMapper.findConversations().stream()
        .map(
            (row) ->
                new ConversationDto(
                    row.getId(),
                    row.getContact(),
                    new LocalizedTextDto(row.getLastMessageHr(), row.getLastMessageEn()),
                    row.getTimeLabel()))
        .toList();
  }
}
