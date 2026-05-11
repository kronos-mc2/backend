package hr.kronos.backend.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentProvider implements PaymentProvider {
  private final String publishableKey;
  private final boolean stubEnabled;

  public StripePaymentProvider(
      @Value("${app.payments.stripe.publishable-key:}") String publishableKey,
      @Value("${app.payments.stub-enabled:true}") boolean stubEnabled) {
    this.publishableKey = publishableKey;
    this.stubEnabled = stubEnabled;
  }

  @Override
  public String name() {
    return "stripe";
  }

  @Override
  public String mode() {
    return stubEnabled ? "stub" : "live";
  }

  @Override
  public String publishableKey() {
    return publishableKey == null || publishableKey.isBlank() ? null : publishableKey;
  }

  @Override
  public ProviderCheckout createCheckout(ProviderCheckoutRequest request) {
    if (!stubEnabled) {
      throw new IllegalStateException("Stripe live provider is not wired yet.");
    }

    String paymentId = "pi_stub_" + request.orderId().replace("-", "_");
    return new ProviderCheckout(
        "checkout_stub_" + request.orderId(),
        paymentId,
        null,
        paymentId + "_secret_stub");
  }

  @Override
  public ProviderPaymentResult confirmPayment(String providerPaymentId, String confirmationToken) {
    if (!stubEnabled) {
      throw new IllegalStateException("Stripe live provider is not wired yet.");
    }

    return new ProviderPaymentResult("succeeded", null);
  }
}
