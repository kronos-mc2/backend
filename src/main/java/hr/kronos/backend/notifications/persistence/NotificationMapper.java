package hr.kronos.backend.notifications.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {
  NotificationPreferencesRow findPreferences(@Param("userId") String userId);

  int upsertPreferences(
      @Param("userId") String userId,
      @Param("directMessagesEnabled") boolean directMessagesEnabled,
      @Param("groupMessagesEnabled") boolean groupMessagesEnabled);

  int upsertPushToken(
      @Param("id") String id,
      @Param("userId") String userId,
      @Param("token") String token,
      @Param("platform") String platform,
      @Param("deviceId") String deviceId,
      @Param("locale") String locale);

  int disablePushToken(@Param("userId") String userId, @Param("token") String token);

  int disablePushTokenById(@Param("tokenId") String tokenId);

  boolean isRoomMutedForUser(@Param("roomId") String roomId, @Param("userId") String userId);

  int muteRoom(@Param("roomId") String roomId, @Param("userId") String userId);

  int unmuteRoom(@Param("roomId") String roomId, @Param("userId") String userId);

  List<PushNotificationRecipientRow> findPushRecipientsForChatMessage(
      @Param("roomId") String roomId,
      @Param("senderUserId") String senderUserId);
}
