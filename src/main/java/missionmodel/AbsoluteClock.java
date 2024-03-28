package missionmodel;

import gov.nasa.jpl.aerie.contrib.streamline.core.Resources;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.time.Time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class AbsoluteClock {

  private Instant startTime;

  AbsoluteClock(final Instant startTime) {
    this.startTime = startTime;
  }

  public Instant now() {
    return startTime.plusMillis(Resources.currentTime().in(Duration.MILLISECONDS));
  }

}
