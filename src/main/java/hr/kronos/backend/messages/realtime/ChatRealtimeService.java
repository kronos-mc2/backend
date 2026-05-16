package hr.kronos.backend.messages.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hr.kronos.backend.messages.persistence.ChatMemberRow;
import hr.kronos.backend.messages.persistence.MessageMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChatRealtimeService {
  public static final String MESSAGE_CREATED = "message.created";
  public static final String POLL_UPDATED = "poll.updated";
  public static final String ROOM_UPDATED = "room.updated";

  private final MessageMapper messageMapper;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ChatRealtimeSessionRegistry sessionRegistry;

  public ChatRealtimeService(MessageMapper messageMapper, ChatRealtimeSessionRegistry sessionRegistry) {
    this.messageMapper = messageMapper;
    this.sessionRegistry = sessionRegistry;
  }

  public void publishToRoom(String roomId, ChatRealtimeEvent event) {
    List<String> memberUserIds =
        messageMapper.findMembersForRoom(roomId).stream().map(ChatMemberRow::getUserId).distinct().toList();
    if (memberUserIds.isEmpty()) {
      return;
    }

    try {
      sessionRegistry.sendToUsers(memberUserIds, objectMapper.writeValueAsString(event));
    } catch (JsonProcessingException _) {
      // A realtime miss should not fail the already-committed chat action.
    }
  }
}
