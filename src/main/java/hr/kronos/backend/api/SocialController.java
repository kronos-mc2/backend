package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.FriendDto;
import hr.kronos.backend.social.SocialService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social")
public class SocialController {
  private final SocialService socialService;

  public SocialController(SocialService socialService) {
    this.socialService = socialService;
  }

  @GetMapping("/friends")
  public List<FriendDto> getFriends() {
    return socialService.getFriends();
  }
}
