package com.example.application;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Common date and time utility tools that can be used by multiple agents.
 */
public class DateTools {

  @FunctionTool(description = "Return current date in yyyy-MM-dd format")
  public String getCurrentDate() {
    return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  @FunctionTool(
    description = "Return current date and time in yyyy-MM-dd HH:mm format for schedule planning"
  )
  public String getCurrentDateTime() {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
  }

  @FunctionTool(description = "Return date for N days from now in yyyy-MM-dd format")
  public String getDateInDays(@Description("Number of days from today") int days) {
    return LocalDate.now().plusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  @FunctionTool(description = "Check if a date is a weekend (Saturday or Sunday)")
  public boolean isWeekend(@Description("Date in yyyy-MM-dd format") String dateStr) {
    try {
      LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
      DayOfWeek dayOfWeek = date.getDayOfWeek();
      return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    } catch (Exception e) {
      return false;
    }
  }
}
