package com.example.infrastructure;

import com.example.domain.Clock;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class DefaultZoneClock implements Clock {

  @Override
  public LocalDateTime now() {
    return LocalDateTime.now();
  }
}
