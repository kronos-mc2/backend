package hr.kronos.backend.events;

import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.payments.persistence.PaymentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventTicketService {
  private static final String ATTENDANCE_MODE_PAID = "paid";

  private final PaymentMapper paymentMapper;

  public EventTicketService(PaymentMapper paymentMapper) {
    this.paymentMapper = paymentMapper;
  }

  @Transactional
  public void syncTicketProductForEvent(EventRow event) {
    if (!ATTENDANCE_MODE_PAID.equals(event.getAttendanceMode())) {
      return;
    }

    paymentMapper.upsertTicketProduct(
        ticketProductId(event.getId()),
        event.getId(),
        trimProductName(event.getTitleHr()),
        event.getPriceAmount(),
        event.getPriceCurrency());
  }

  @Transactional(readOnly = true)
  public boolean hasCompletedTicketPayment(String eventId, String userId) {
    return paymentMapper.hasSucceededTicketOrder(eventId, userId);
  }

  private String ticketProductId(String eventId) {
    return "ticket-" + eventId.substring(0, Math.min(eventId.length(), 57));
  }

  private String trimProductName(String value) {
    if (value == null || value.isBlank()) {
      return "Event ticket";
    }

    String normalized = value.trim().replaceAll("\\s+", " ");
    return normalized.length() > 180 ? normalized.substring(0, 180) : normalized;
  }
}
