package dev.aparadhkavach.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// scanBasePackages reaches up to dev.aparadhkavach.commons so GlobalExceptionHandler
// (aparadhkavach-commons) is picked up — it lives outside this service's own package.
@SpringBootApplication(scanBasePackages = "dev.aparadhkavach")
public class NotificationApplication {
  public static void main(String[] args) {
    SpringApplication.run(NotificationApplication.class, args);
  }
}
