package hr.kronos.backend.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.kronos.backend.api.dto.UpdateProfileRequest;
import hr.kronos.backend.auth.persistence.AuthMapper;
import hr.kronos.backend.auth.persistence.UserRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
  @Mock private AuthMapper authMapper;
  @InjectMocks private ProfileService service;

  private final String avatarUrl = "/api/users/usr_1/avatar/content?v=existing";

  @BeforeEach
  void setUp() {
    UserRow user = new UserRow();
    user.setId("usr_1");
    user.setEmail("profile@example.com");
    user.setAvatarUrl(avatarUrl);
    when(authMapper.findById("usr_1")).thenReturn(user);
  }

  @Test
  void preservesUploadedAvatarWhenEditingProfile() {
    var result = service.updateProfile(new UpdateProfileRequest("Updated name", "Bio", avatarUrl), "usr_1");

    assertEquals(avatarUrl, result.avatarUrl());
    verify(authMapper).updateProfile("usr_1", "Updated name", "Bio", avatarUrl);
  }

  @Test
  void rejectsOtherRelativeAvatarUrls() {
    var exception = assertThrows(ResponseStatusException.class, () -> service.updateProfile(
        new UpdateProfileRequest("Updated name", "Bio", "/api/users/usr_2/avatar/content?v=other"), "usr_1"));

    assertEquals(400, exception.getStatusCode().value());
  }
}
