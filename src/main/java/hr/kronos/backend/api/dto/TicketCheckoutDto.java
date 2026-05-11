package hr.kronos.backend.api.dto;

import java.math.BigDecimal;

public record TicketCheckoutDto(
    String orderId,
    String eventId,
    String provider,
    String providerMode,
    String status,
    BigDecimal amount,
    String currency,
    String checkoutUrl,
    String clientSecret,
    String publishableKey) {}
