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
  private final ConcurrentMap<WebSocketSession, Object> sessionLocks = new ConcurrentHashMap<>();

  public void register(WebSocketSession session) {
    String userId = getUserId(session);
    if (userId == null) {
      return;
    }

    sessionsByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    sessionLocks.computeIfAbsent(session, _ -> new Object());
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
    sessionLocks.remove(session);
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

  public boolean hasOpenSession(String userId) {
    Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
    if (sessions == null || sessions.isEmpty()) {
      return false;
    }

    boolean hasOpenSession = false;
    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        hasOpenSession = true;
      } else {
        unregister(session);
      }
    }
    return hasOpenSession;
  }

  private void send(WebSocketSession session, TextMessage message) {
    if (!session.isOpen()) {
      unregister(session);
      return;
    }

    try {
      Object lock = sessionLocks.computeIfAbsent(session, _ -> new Object());
      synchronized (lock) {
        session.sendMessage(message);
      }
    } catch (IOException _) {
      unregister(session);
    }
  }

  private String getUserId(WebSocketSession session) {
    Object userId = session.getAttributes().get(USER_ID_ATTRIBUTE);
    return userId instanceof String value && !value.isBlank() ? value : null;
  }
}
