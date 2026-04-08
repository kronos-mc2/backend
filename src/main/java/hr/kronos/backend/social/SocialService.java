package hr.kronos.backend.social;

import hr.kronos.backend.api.dto.FriendDto;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.social.persistence.SocialMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SocialService {
  private final SocialMapper socialMapper;

  public SocialService(SocialMapper socialMapper) {
    this.socialMapper = socialMapper;
  }

  public List<FriendDto> getFriends() {
    return socialMapper.findFriends().stream()
        .map((row) -> new FriendDto(row.getId(), row.getName(), new LocalizedTextDto(row.getStatusHr(), row.getStatusEn())))
        .toList();
  }
}
