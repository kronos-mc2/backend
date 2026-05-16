package hr.kronos.backend.payments;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.ConfirmTicketCheckoutRequest;
import hr.kronos.backend.api.dto.TicketCheckoutDto;
import hr.kronos.backend.api.dto.TicketCheckoutResultDto;
import hr.kronos.backend.api.dto.TransactionDto;
import hr.kronos.backend.events.EventService;
import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.payments.persistence.PaymentMapper;
import hr.kronos.backend.payments.persistence.TicketOrderRow;
import hr.kronos.backend.payments.persistence.TicketProductRow;
import hr.kronos.backend.payments.persistence.TicketTransactionRow;
import hr.kronos.backend.profile.persistence.TransactionRow;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
  private static final String ORDER_PENDING = "pending";
  private static final String ORDER_SUCCEEDED = "succeeded";
  private static final String EVENT_ATTENDANCE_PAID = "paid";
  private static final String EVENT_STATUS_PUBLISHED = "published";

  private final EventMapper eventMapper;
  private final EventService eventService;
  private final PaymentMapper paymentMapper;
  private final PaymentProvider paymentProvider;

  public PaymentService(
      EventMapper eventMapper,
      EventService eventService,
      PaymentMapper paymentMapper,
      PaymentProvider paymentProvider) {
    this.eventMapper = eventMapper;
    this.eventService = eventService;
    this.paymentMapper = paymentMapper;
    this.paymentProvider = paymentProvider;
  }

  public void syncTicketProductForEvent(EventRow event) {
    if (!EVENT_ATTENDANCE_PAID.equals(event.getAttendanceMode())) {
      return;
    }

    paymentMapper.upsertTicketProduct(
        ticketProductId(event.getId()),
        event.getId(),
        trimProductName(event.getTitleHr()),
        event.getPriceAmount(),
        event.getPriceCurrency());
  }

  public boolean hasCompletedTicketPayment(String eventId, String userId) {
    return paymentMapper.hasSucceededTicketOrder(eventId, userId);
  }

  @Transactional
  public TicketCheckoutDto createTicketCheckout(String eventId, String userId) {
    EventRow event = requirePaidJoinableEvent(eventId, userId);
    if (paymentMapper.hasSucceededTicketOrder(eventId, userId)) {
      AppEventDto joinedEvent = eventService.joinEventAfterPayment(eventId, userId);
      return new TicketCheckoutDto(
          null,
          joinedEvent.id(),
          paymentProvider.name(),
          paymentProvider.mode(),
          ORDER_SUCCEEDED,
          event.getPriceAmount(),
          event.getPriceCurrency(),
          null,
          null,
          paymentProvider.publishableKey());
    }

    TicketProductRow product = requireTicketProduct(event);
    String orderId = "order-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
    PaymentProvider.ProviderCheckout providerCheckout =
        createProviderCheckout(
            new PaymentProvider.ProviderCheckoutRequest(
                orderId,
                eventId,
                product.getUnitAmount(),
                product.getCurrency(),
                event.getTitleHr()));

    TicketOrderRow order = new TicketOrderRow();
    order.setId(orderId);
    order.setEventId(eventId);
    order.setUserId(userId);
    order.setTicketProductId(product.getId());
    order.setProvider(paymentProvider.name());
    order.setProviderOrderId(providerCheckout.providerOrderId());
    order.setProviderPaymentId(providerCheckout.providerPaymentId());
    order.setAmount(product.getUnitAmount());
    order.setCurrency(product.getCurrency());
    order.setStatus(ORDER_PENDING);
    order.setCheckoutUrl(providerCheckout.checkoutUrl());
    order.setClientSecret(providerCheckout.clientSecret());
    paymentMapper.insertOrder(order);
    paymentMapper.insertPayment(
        "pay-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18),
        order.getId(),
        paymentProvider.name(),
        providerCheckout.providerPaymentId(),
        product.getUnitAmount(),
        product.getCurrency(),
        "requires_confirmation");

    return toCheckoutDto(order);
  }

  @Transactional
  public TicketCheckoutResultDto confirmTicketCheckout(
      String orderId, ConfirmTicketCheckoutRequest request, String userId) {
    TicketOrderRow order = paymentMapper.findOrderForUser(orderId, userId);
    if (order == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket order not found.");
    }
    if (!ORDER_PENDING.equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket order is not pending.");
    }

    requirePaidJoinableEvent(order.getEventId(), userId);
    PaymentProvider.ProviderPaymentResult result =
        confirmProviderPayment(order.getProviderPaymentId(), request == null ? null : request.confirmationToken());
    if (!ORDER_SUCCEEDED.equals(result.status())) {
      throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment was not completed.");
    }

    paymentMapper.markPaymentSucceeded(orderId, order.getProviderPaymentId());
    paymentMapper.markOrderSucceeded(orderId);
    paymentMapper.insertTicketTransaction(newTicketTransaction(order, orderId, userId));
    AppEventDto event = eventService.joinEventAfterPayment(order.getEventId(), userId);
    TicketOrderRow updatedOrder = paymentMapper.findOrderForUser(orderId, userId);
    TransactionRow transaction = paymentMapper.findTransactionByOrderId(orderId);
    return new TicketCheckoutResultDto(toCheckoutDto(updatedOrder), event, toTransactionDto(transaction));
  }

  private TicketTransactionRow newTicketTransaction(TicketOrderRow order, String orderId, String userId) {
    TicketTransactionRow transaction = new TicketTransactionRow();
    transaction.setId("txn-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
    transaction.setOrderId(orderId);
    transaction.setUserId(userId);
    transaction.setEventId(order.getEventId());
    transaction.setProvider(order.getProvider());
    transaction.setProviderReference(order.getProviderPaymentId());
    transaction.setAmount(order.getAmount());
    transaction.setCurrency(order.getCurrency());
    transaction.setDescription("Event ticket");
    return transaction;
  }

  private EventRow requirePaidJoinableEvent(String eventId, String userId) {
    EventRow event = eventMapper.findAccessibleById(eventId, userId);
    if (event == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
    }
    if (!EVENT_ATTENDANCE_PAID.equals(event.getAttendanceMode())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event does not require ticket checkout.");
    }
    if (!EVENT_STATUS_PUBLISHED.equals(event.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event cannot be joined.");
    }
    if (isJoined(event.getUserParticipantStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is already joined.");
    }
    if (event.getCapacity() != null && event.getParticipantCount() >= event.getCapacity()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is full.");
    }
    return event;
  }

  private PaymentProvider.ProviderCheckout createProviderCheckout(PaymentProvider.ProviderCheckoutRequest request) {
    try {
      return paymentProvider.createCheckout(request);
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, exception.getMessage());
    }
  }

  private PaymentProvider.ProviderPaymentResult confirmProviderPayment(String providerPaymentId, String confirmationToken) {
    try {
      return paymentProvider.confirmPayment(providerPaymentId, confirmationToken);
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, exception.getMessage());
    }
  }

  private TicketProductRow requireTicketProduct(EventRow event) {
    TicketProductRow product = paymentMapper.findTicketProductByEventId(event.getId());
    if (product != null) {
      return product;
    }

    syncTicketProductForEvent(event);
    product = paymentMapper.findTicketProductByEventId(event.getId());
    if (product == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket product is not configured.");
    }
    return product;
  }

  private TicketCheckoutDto toCheckoutDto(TicketOrderRow order) {
    return new TicketCheckoutDto(
        order.getId(),
        order.getEventId(),
        order.getProvider(),
        paymentProvider.mode(),
        order.getStatus(),
        order.getAmount(),
        order.getCurrency(),
        order.getCheckoutUrl(),
        order.getClientSecret(),
        paymentProvider.publishableKey());
  }

  private TransactionDto toTransactionDto(TransactionRow row) {
    if (row == null) {
      return null;
    }

    return new TransactionDto(
        row.getId(),
        row.getEventId(),
        row.getEventTitle(),
        row.getOrderId(),
        row.getTransactionType(),
        row.getAmount(),
        row.getCurrency(),
        row.getStatus(),
        row.getDescription(),
        row.getPaymentProvider(),
        row.getProviderReference(),
        row.getCreatedAt() == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(row.getCreatedAt()));
  }

  private boolean isJoined(String status) {
    return "joined".equals(status) || "approved".equals(status) || "waitlisted".equals(status);
  }

  private String trimProductName(String value) {
    if (value == null || value.isBlank()) {
      return "Event ticket";
    }
    String normalized = value.trim().replaceAll("\\s+", " ");
    return normalized.length() > 180 ? normalized.substring(0, 180) : normalized;
  }

  private String ticketProductId(String eventId) {
    return "ticket-" + eventId.substring(0, Math.min(eventId.length(), 57));
  }
}
