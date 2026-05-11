package hr.kronos.backend.payments.persistence;

import hr.kronos.backend.profile.persistence.TransactionRow;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {
  TicketProductRow findTicketProductByEventId(@Param("eventId") String eventId);

  int upsertTicketProduct(
      @Param("id") String id,
      @Param("eventId") String eventId,
      @Param("name") String name,
      @Param("unitAmount") BigDecimal unitAmount,
      @Param("currency") String currency);

  int insertOrder(TicketOrderRow order);

  TicketOrderRow findOrderForUser(@Param("orderId") String orderId, @Param("userId") String userId);

  boolean hasSucceededTicketOrder(@Param("eventId") String eventId, @Param("userId") String userId);

  int insertPayment(
      @Param("id") String id,
      @Param("orderId") String orderId,
      @Param("provider") String provider,
      @Param("providerPaymentId") String providerPaymentId,
      @Param("amount") BigDecimal amount,
      @Param("currency") String currency,
      @Param("status") String status);

  int markPaymentSucceeded(@Param("orderId") String orderId, @Param("providerPaymentId") String providerPaymentId);

  int markOrderSucceeded(@Param("orderId") String orderId);

  int insertTicketTransaction(
      @Param("id") String id,
      @Param("orderId") String orderId,
      @Param("userId") String userId,
      @Param("eventId") String eventId,
      @Param("provider") String provider,
      @Param("providerReference") String providerReference,
      @Param("amount") BigDecimal amount,
      @Param("currency") String currency,
      @Param("description") String description);

  TransactionRow findTransactionByOrderId(@Param("orderId") String orderId);
}
