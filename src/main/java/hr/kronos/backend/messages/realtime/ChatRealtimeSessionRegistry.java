package hr.kronos.backend.messages.realtime;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ChatRealtimeSessionRegistry {
  public static final String USER_ID_ATTRIBUTE = "userId";

  private final ConcurrentMap<String, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();

  public void register(WebSocketSession session) {
    String userId = getUserId(session);
    if (userId == null) {
      return;
    }

    sessionsByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
  }

  public void unregister(WebSocketSession session) {
    String userId = getUserId(session);
    if (userId == null) {
      return;
    }

    Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
    if (sessions == null) {
      return;
    }

    sessions.remove(session);
    if (sessions.isEmpty()) {
      sessionsByUserId.remove(userId, sessions);
    }
  }

  public void sendToUsers(Collection<String> userIds, String payload) {
    TextMessage message = new TextMessage(payload);
    for (String userId : userIds) {
      Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
      if (sessions == null) {
        continue;
      }

      for (WebSocketSession session : sessions) {
        send(session, message);
      }
    }
  }

  private void send(WebSocketSession session, TextMessage message) {
    if (!session.isOpen()) {
      unregister(session);
      return;
    }

    try {
      synchronized (session) {
        session.sendMessage(message);
      }
    } catch (IOException ignored) {
      unregister(session);
    }
  }

  private String getUserId(WebSocketSession session) {
    Object userId = session.getAttributes().get(USER_ID_ATTRIBUTE);
    return userId instanceof String value && !value.isBlank() ? value : null;
  }
}
