package com.example;

import com.example.domain.Clock;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
