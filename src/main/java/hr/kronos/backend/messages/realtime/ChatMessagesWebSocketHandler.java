package hr.kronos.backend.messages.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ChatMessagesWebSocketHandler extends TextWebSocketHandler {
  private final ChatRealtimeSessionRegistry sessionRegistry;

  public ChatMessagesWebSocketHandler(ChatRealtimeSessionRegistry sessionRegistry) {
    this.sessionRegistry = sessionRegistry;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessionRegistry.register(session);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    // Chat writes stay on REST endpoints. Client messages are reserved for future lightweight commands.
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessionRegistry.unregister(session);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    sessionRegistry.unregister(session);
  }
}
