package scheduling;

import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

import java.time.Instant;
import java.util.Arrays;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.*;

public class SchedulingUtils {

  public static Instant instantMinusDuration(Instant instant, Duration duration) {
    return instantPlusDuration(instant, duration.times(-1));
  }

  public static Instant instantPlusDuration(Instant instant, Duration... durations) {
    final var sum = Arrays.stream(durations).reduce(ZERO, Duration::plus);
    return instant.plusNanos(sum.in(MICROSECONDS) * 1000L);
  }

  public static Duration durationBetweenInstants(Instant before, Instant after) {
    return duration(after.toEpochMilli() - before.toEpochMilli(), MILLISECONDS);
  }

}
