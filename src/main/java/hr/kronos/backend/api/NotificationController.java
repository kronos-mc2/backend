package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.NotificationPreferencesDto;
import hr.kronos.backend.api.dto.RegisterPushTokenRequest;
import hr.kronos.backend.api.dto.UpdateNotificationPreferencesRequest;
import hr.kronos.backend.auth.AuthPrincipal;
import hr.kronos.backend.notifications.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/notifications")
public class NotificationController {
  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping("/preferences")
  public NotificationPreferencesDto getPreferences(Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return notificationService.getPreferences(principal.userId());
  }

  @PatchMapping("/preferences")
  public NotificationPreferencesDto updatePreferences(
      @RequestBody UpdateNotificationPreferencesRequest request,
      Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return notificationService.updatePreferences(request, principal.userId());
  }

  @PostMapping("/push-tokens")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void registerPushToken(@RequestBody RegisterPushTokenRequest request, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    notificationService.registerPushToken(request, principal.userId());
  }

  @DeleteMapping("/push-tokens")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePushToken(@RequestParam String token, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    notificationService.deletePushToken(token, principal.userId());
  }
}
