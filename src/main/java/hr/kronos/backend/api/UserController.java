package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.ProfileActivityDto;
import hr.kronos.backend.api.dto.TransactionDto;
import hr.kronos.backend.api.dto.UpdateProfileRequest;
import hr.kronos.backend.auth.AuthPrincipal;
import hr.kronos.backend.auth.dto.AuthUserDto;
import hr.kronos.backend.profile.ProfileService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
  private final ProfileService profileService;

  public UserController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @PatchMapping("/profile")
  public AuthUserDto updateProfile(@RequestBody UpdateProfileRequest request, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return profileService.updateProfile(request, principal.userId());
  }

  @GetMapping("/activity")
  public ProfileActivityDto getActivity(Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return profileService.getActivity(principal.userId());
  }

  @GetMapping("/transactions")
  public List<TransactionDto> getTransactions(Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return profileService.getTransactions(principal.userId());
  }
}
