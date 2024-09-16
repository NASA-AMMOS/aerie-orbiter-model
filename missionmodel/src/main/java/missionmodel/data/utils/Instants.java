package missionmodel.data.utils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

public final class Instants {

  private Instants() {
    throw new UnsupportedOperationException("This class is non-instantiable");
  }

  public static final DateTimeFormatter FORMATTER =
      new DateTimeFormatterBuilder()
          .appendPattern("uuuu-DDD'T'HH:mm:ss")
          .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
          .appendZoneId()
          .toFormatter()
          .withZone(ZoneOffset.UTC);

  /**
   * Utility method for parsing ISO-8601-style timestamps or DOY-string-style timestamps
   *
   * <p>This will throw a {@link DateTimeParseException} if the input string is not conformant to
   * either style.
   *
   * @param str a string in {@link DateTimeFormatter#ISO_INSTANT} format OR a DOY-style timestamp
   *     ({@code YYYY-DOYThh:mm:ss[.ssssss]})
   * @return the parsed instant
   */
  public static Instant parseLeniently(String str) {
    try {
      return Instant.parse(str);
    } catch (DateTimeParseException exception) {
      return parseFromDOYString(str);
    }
  }

  public static Instant parseFromDOYString(String str) {
    return Instant.from(FORMATTER.parse(str));
  }

  public static String formatToDOYString(Instant instant) {
    return FORMATTER.format(instant);
  }
}
