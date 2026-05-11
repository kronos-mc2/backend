package hr.kronos.backend.payments;

import java.math.BigDecimal;

public interface PaymentProvider {
  String name();

  String mode();

  String publishableKey();

  ProviderCheckout createCheckout(ProviderCheckoutRequest request);

  ProviderPaymentResult confirmPayment(String providerPaymentId, String confirmationToken);

  record ProviderCheckoutRequest(String orderId, String eventId, BigDecimal amount, String currency, String description) {}

  record ProviderCheckout(String providerOrderId, String providerPaymentId, String checkoutUrl, String clientSecret) {}

  record ProviderPaymentResult(String status, String failureReason) {}
}
