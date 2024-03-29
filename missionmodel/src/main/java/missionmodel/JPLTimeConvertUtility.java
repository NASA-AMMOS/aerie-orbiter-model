package missionmodel;

import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.time.Time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class JPLTimeConvertUtility {
  public static Time nowJplTime(AbsoluteClock absClock) {
    return Time.fromTimezone(ZonedDateTime.ofInstant(absClock.now(), ZoneId.of("UTC")));
  }

  public static Time jplTimeFromUTCInstant( Instant time ) {
    return Time.fromTimezone(ZonedDateTime.ofInstant(time, ZoneId.of("UTC")));
  }

  public static Duration getDuration( gov.nasa.jpl.time.Duration jplTimeDur ) {
    return Duration.duration(jplTimeDur.getMicroseconds(), Duration.MICROSECONDS);
  }

  public static gov.nasa.jpl.time.Duration getJplTimeDur( Duration aerieDur) {
    return gov.nasa.jpl.time.Duration.fromMilliseconds(aerieDur.ratioOver(Duration.MILLISECONDS));
  }

}
