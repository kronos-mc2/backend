package hr.kronos.backend.messages.realtime;

import hr.kronos.backend.auth.AuthPrincipal;
import hr.kronos.backend.auth.JwtService;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ChatWebSocketAuthInterceptor implements HandshakeInterceptor {
  private final JwtService jwtService;

  public ChatWebSocketAuthInterceptor(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    String token = resolveBearerToken(request);
    if (token == null) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    try {
      Claims claims = jwtService.parseClaims(token);
      String userId = claims.getSubject();
      if (userId == null || userId.isBlank()) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }

      attributes.put(ChatRealtimeSessionRegistry.USER_ID_ATTRIBUTE, userId);
      attributes.put(
          "principal",
          new AuthPrincipal(userId, claims.get("email", String.class), claims.get("name", String.class)));
      return true;
    } catch (Exception ignored) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}

  private String resolveBearerToken(ServerHttpRequest request) {
    List<String> authorizationHeaders = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
    if (authorizationHeaders != null) {
      for (String header : authorizationHeaders) {
        if (header != null && header.startsWith("Bearer ")) {
          return header.substring(7);
        }
      }
    }

    return UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("access_token");
  }
}
