package hr.kronos.backend.messages.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {
  private final ChatMessagesWebSocketHandler messagesWebSocketHandler;
  private final ChatWebSocketAuthInterceptor authInterceptor;
  private final String[] allowedOrigins;

  public ChatWebSocketConfig(
      ChatMessagesWebSocketHandler messagesWebSocketHandler,
      ChatWebSocketAuthInterceptor authInterceptor,
      @Value("${app.websocket.allowed-origins:*}") String[] allowedOrigins) {
    this.messagesWebSocketHandler = messagesWebSocketHandler;
    this.authInterceptor = authInterceptor;
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(messagesWebSocketHandler, "/ws/messages")
        .addInterceptors(authInterceptor)
        .setAllowedOrigins(allowedOrigins);
  }
}
