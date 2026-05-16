package hr.kronos.backend.notifications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hr.kronos.backend.api.dto.ChatMessageDto;
import hr.kronos.backend.api.dto.NotificationPreferencesDto;
import hr.kronos.backend.api.dto.RegisterPushTokenRequest;
import hr.kronos.backend.api.dto.UpdateNotificationPreferencesRequest;
import hr.kronos.backend.messages.persistence.MessageMapper;
import hr.kronos.backend.notifications.persistence.NotificationMapper;
import hr.kronos.backend.notifications.persistence.NotificationPreferencesRow;
import hr.kronos.backend.notifications.persistence.PushNotificationRecipientRow;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
  private static final int MAX_TOKEN_LENGTH = 500;
  private static final int MAX_DEVICE_ID_LENGTH = 200;
  private static final int MAX_NOTIFICATION_BODY_LENGTH = 180;
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final NotificationMapper notificationMapper;
  private final MessageMapper messageMapper;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final boolean pushEnabled;
  private final String expoPushEndpoint;

  public NotificationService(
      NotificationMapper notificationMapper,
      MessageMapper messageMapper,
      ObjectMapper objectMapper,
      @Value("${app.notifications.push.enabled:true}") boolean pushEnabled,
      @Value("${app.notifications.expo.endpoint:https://exp.host/--/api/v2/push/send}") String expoPushEndpoint) {
    this.notificationMapper = notificationMapper;
    this.messageMapper = messageMapper;
    this.objectMapper = objectMapper;
    this.pushEnabled = pushEnabled;
    this.expoPushEndpoint = expoPushEndpoint;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  }

  public NotificationPreferencesDto getPreferences(String userId) {
    NotificationPreferencesRow row = notificationMapper.findPreferences(userId);
    if (row == null) {
      return new NotificationPreferencesDto(true, true);
    }

    return new NotificationPreferencesDto(
        row.getDirectMessagesEnabled() == null || row.getDirectMessagesEnabled(),
        row.getGroupMessagesEnabled() == null || row.getGroupMessagesEnabled());
  }

  public NotificationPreferencesDto updatePreferences(UpdateNotificationPreferencesRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    NotificationPreferencesDto current = getPreferences(userId);
    boolean directMessagesEnabled = request.directMessagesEnabled() == null
        ? current.directMessagesEnabled()
        : request.directMessagesEnabled();
    boolean groupMessagesEnabled = request.groupMessagesEnabled() == null
        ? current.groupMessagesEnabled()
        : request.groupMessagesEnabled();

    notificationMapper.upsertPreferences(userId, directMessagesEnabled, groupMessagesEnabled);
    return new NotificationPreferencesDto(directMessagesEnabled, groupMessagesEnabled);
  }

  public void registerPushToken(RegisterPushTokenRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    String token = trimToNull(request.token());
    if (token == null || token.length() > MAX_TOKEN_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid push token.");
    }

    String platform = normalizePlatform(request.platform());
    String deviceId = trimToNull(request.deviceId());
    if (deviceId != null && deviceId.length() > MAX_DEVICE_ID_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deviceId is too long.");
    }

    notificationMapper.upsertPushToken("push-" + UUID.randomUUID(), userId, token, platform, deviceId);
  }

  public void deletePushToken(String token, String userId) {
    String normalizedToken = trimToNull(token);
    if (normalizedToken == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token is required.");
    }

    notificationMapper.disablePushToken(userId, normalizedToken);
  }

  public boolean isChatMuted(String roomId, String userId) {
    requireRoomMember(roomId, userId);
    return notificationMapper.isRoomMutedForUser(roomId, userId);
  }

  public boolean setChatMuted(String roomId, boolean muted, String userId) {
    requireRoomMember(roomId, userId);
    if (muted) {
      notificationMapper.muteRoom(roomId, userId);
      return true;
    }

    notificationMapper.unmuteRoom(roomId, userId);
    return false;
  }

  @Async
  public void sendChatMessagePush(String roomId, ChatMessageDto message, String senderUserId) {
    if (!pushEnabled) {
      return;
    }

    List<PushNotificationRecipientRow> recipients =
        notificationMapper.findPushRecipientsForChatMessage(roomId, senderUserId);
    if (recipients.isEmpty()) {
      return;
    }

    List<Map<String, Object>> payloads = recipients.stream()
        .map(recipient -> toExpoPushPayload(recipient, message))
        .toList();

    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(expoPushEndpoint))
          .timeout(Duration.ofSeconds(6))
          .header("Accept", "application/json")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payloads)))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        LOGGER.warn("Expo push request failed with status {}", response.statusCode());
        return;
      }
      disableInvalidTokens(response.body(), recipients);
    } catch (IOException exception) {
      LOGGER.warn("Expo push request failed.", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      LOGGER.warn("Expo push request interrupted.", exception);
    }
  }

  private Map<String, Object> toExpoPushPayload(PushNotificationRecipientRow recipient, ChatMessageDto message) {
    return Map.of(
        "to", recipient.getToken(),
        "sound", "default",
        "title", firstNonBlank(recipient.getRoomTitle(), "Gdje i Kada"),
        "body", trimNotificationBody(messageBody(message)),
        "data", Map.of(
            "type", "chat_message",
            "roomId", message.roomId(),
            "messageId", message.id()));
  }

  private void disableInvalidTokens(String responseBody, List<PushNotificationRecipientRow> recipients) {
    try {
      Map<String, Object> response = objectMapper.readValue(responseBody, MAP_TYPE);
      Object rawData = response.get("data");
      if (!(rawData instanceof List<?> data)) {
        return;
      }

      for (int index = 0; index < data.size() && index < recipients.size(); index++) {
        Object item = data.get(index);
        if (item instanceof Map<?, ?> ticket
            && ticket.get("details") instanceof Map<?, ?> detailMap
            && "DeviceNotRegistered".equals(detailMap.get("error"))) {
          notificationMapper.disablePushTokenById(recipients.get(index).getTokenId());
        }
      }
    } catch (IOException exception) {
      LOGGER.debug("Could not parse Expo push response.", exception);
    }
  }

  private String messageBody(ChatMessageDto message) {
    if ("event_share".equals(message.type())) {
      if (message.event() != null && message.event().title() != null) {
        return firstNonBlank(message.event().title().hr(), message.event().title().en(), "Event");
      }
      return "Event";
    }
    if ("poll".equals(message.type())) {
      return firstNonBlank(message.body(), "Poll");
    }
    return firstNonBlank(message.body(), "Nova poruka");
  }

  private String trimNotificationBody(String body) {
    if (body.length() <= MAX_NOTIFICATION_BODY_LENGTH) {
      return body;
    }
    return body.substring(0, MAX_NOTIFICATION_BODY_LENGTH - 1).trim() + "...";
  }

  private void requireRoomMember(String roomId, String userId) {
    if (messageMapper.findRoomForUser(roomId, userId) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found.");
    }
  }

  private String normalizePlatform(String platform) {
    String normalized = trimToNull(platform);
    if (normalized == null) {
      return "unknown";
    }

    String lower = normalized.toLowerCase(Locale.ROOT);
    return switch (lower) {
      case "ios", "android", "web" -> lower;
      default -> "unknown";
    };
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      String normalized = trimToNull(value);
      if (normalized != null) {
        return normalized;
      }
    }
    return "";
  }
}
