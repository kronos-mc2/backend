package hr.kronos.backend.messages.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MessageMapper {
  List<ConversationRow> findConversations();

  ConversationRow findConversationById(@Param("id") String id);

  int updateConversationSharePreview(
      @Param("id") String id,
      @Param("lastMessageHr") String lastMessageHr,
      @Param("lastMessageEn") String lastMessageEn);
}
