package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.CreateFriendRequestRequest;
import hr.kronos.backend.api.dto.FriendDto;
import hr.kronos.backend.api.dto.FriendRequestDto;
import hr.kronos.backend.api.dto.RespondFriendRequestRequest;
import hr.kronos.backend.social.SocialService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social")
public class SocialController {
  private final SocialService socialService;

  public SocialController(SocialService socialService) {
    this.socialService = socialService;
  }

  @GetMapping("/friends")
  public List<FriendDto> getFriends(Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return socialService.getFriends(userId);
  }

  @PostMapping("/friend-requests")
  @ResponseStatus(HttpStatus.CREATED)
  public FriendRequestDto createFriendRequest(
      @RequestBody CreateFriendRequestRequest request,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return socialService.createFriendRequest(request, userId);
  }

  @PatchMapping("/friend-requests/{id}")
  public FriendRequestDto respondToFriendRequest(
      @PathVariable String id,
      @RequestBody RespondFriendRequestRequest request,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return socialService.respondToFriendRequest(id, request == null ? null : request.status(), userId);
  }
}
