package com.example.infrastructure;

import com.example.domain.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("prod")
public class DefaultZoneClock implements Clock {

  @Override
  public LocalDateTime now() {
    return LocalDateTime.now();
  }
}
