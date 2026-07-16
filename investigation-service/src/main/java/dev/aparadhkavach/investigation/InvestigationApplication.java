package dev.aparadhkavach.investigation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// scanBasePackages reaches up to dev.aparadhkavach.commons so GlobalExceptionHandler
// (aparadhkavach-commons) is picked up — it lives outside this service's own package.
@SpringBootApplication(scanBasePackages = "dev.aparadhkavach")
public class InvestigationApplication {
  public static void main(String[] args) {
    SpringApplication.run(InvestigationApplication.class, args);
  }
}
