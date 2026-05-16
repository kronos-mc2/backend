package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.ConfirmTicketCheckoutRequest;
import hr.kronos.backend.api.dto.TicketCheckoutDto;
import hr.kronos.backend.api.dto.TicketCheckoutResultDto;
import hr.kronos.backend.payments.PaymentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {
  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping("/events/{eventId}/ticket-checkout")
  public TicketCheckoutDto createTicketCheckout(@PathVariable String eventId, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return paymentService.createTicketCheckout(eventId, userId);
  }

  @PostMapping("/ticket-orders/{orderId}/confirm")
  public TicketCheckoutResultDto confirmTicketCheckout(
      @PathVariable String orderId,
      @RequestBody(required = false) ConfirmTicketCheckoutRequest request,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return paymentService.confirmTicketCheckout(orderId, request, userId);
  }
}
