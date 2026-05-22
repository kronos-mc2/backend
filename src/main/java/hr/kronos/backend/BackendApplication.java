package hr.kronos.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan({
    "hr.kronos.backend.auth.persistence",
    "hr.kronos.backend.events.persistence",
    "hr.kronos.backend.messages.persistence",
    "hr.kronos.backend.notifications.persistence",
    "hr.kronos.backend.payments.persistence",
    "hr.kronos.backend.profile.persistence",
    "hr.kronos.backend.social.persistence"
})
public class BackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
