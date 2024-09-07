package com.example;

import com.example.domain.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("test")
public class FixedClock implements Clock {
  private LocalDateTime now = LocalDateTime.now();

  @Override
  public LocalDateTime now() {
    return now;
  }

  public void setNow(LocalDateTime now) {
    this.now = now;
  }
}
