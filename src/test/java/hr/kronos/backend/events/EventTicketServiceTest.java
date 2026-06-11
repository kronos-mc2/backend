package hr.kronos.backend.events;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.payments.persistence.PaymentMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventTicketServiceTest {
  @Mock
  private PaymentMapper paymentMapper;

  @InjectMocks
  private EventTicketService service;

  @Test
  void syncTicketProductSkipsNonPaidEvents() {
    EventRow event = new EventRow();
    event.setId("evt_open");
    event.setAttendanceMode("open");

    service.syncTicketProductForEvent(event);

    verify(paymentMapper, never()).upsertTicketProduct(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void syncTicketProductUpsertsPaidEventProduct() {
    EventRow event = new EventRow();
    event.setId("evt_paid");
    event.setTitleHr("  Paid   Event  ");
    event.setAttendanceMode("paid");
    event.setPriceAmount(BigDecimal.TEN);
    event.setPriceCurrency("EUR");

    service.syncTicketProductForEvent(event);

    verify(paymentMapper).upsertTicketProduct("ticket-evt_paid", "evt_paid", "Paid Event", BigDecimal.TEN, "EUR");
  }

  @Test
  void hasCompletedTicketPaymentDelegatesToPaymentMapper() {
    when(paymentMapper.hasSucceededTicketOrder("evt_paid", "usr_1")).thenReturn(true);

    assertTrue(service.hasCompletedTicketPayment("evt_paid", "usr_1"));
  }
}
