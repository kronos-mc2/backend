package hr.kronos.backend.profile.persistence;

import hr.kronos.backend.events.persistence.EventRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProfileMapper {
  List<TransactionRow> findTransactionsForUser(@Param("userId") String userId);

  List<EventRow> findRatingCandidates(@Param("userId") String userId);

  List<NotificationRow> findNotificationsForUser(@Param("userId") String userId);
}
