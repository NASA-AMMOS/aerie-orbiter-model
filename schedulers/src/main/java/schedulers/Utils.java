package schedulers;

import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.timeline.Interval;
import gov.nasa.jpl.aerie.timeline.plan.Plan;
import spice.basic.CSPICE;
import spice.basic.SpiceErrorException;

import java.time.Instant;
import java.util.*;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.*;
import static gov.nasa.jpl.aerie.timeline.Interval.Inclusivity.Inclusive;

public class Utils {
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

    public static gov.nasa.jpl.aerie.timeline.Duration $(Duration duration) {
        return new gov.nasa.jpl.aerie.timeline.Duration(duration.in(MICROSECONDS));
    }

    public static Duration $(gov.nasa.jpl.aerie.timeline.Duration duration) {
        return Duration.of(duration.div(new gov.nasa.jpl.aerie.timeline.Duration(1)), MICROSECONDS);
    }

    public static Instant et2instant(double et) {
        try {
            return Instant.parse(CSPICE.et2utc(et, "ISOC", 3) + "Z");
        } catch (SpiceErrorException e) {
            throw new RuntimeException(e);
        }
    }

    public static Instant okToSchedule(Plan plan, String resourceName, boolean value, Instant begint, Instant cutofft, ArrayList<Instant> windows) {
        return okToSchedule(plan, resourceName, SerializedValue.of(value), duration(1, SECOND), durationBetweenInstants(begint, cutofft).plus(duration(24, HOURS)), windows);
    }

    public static Instant okToSchedule(Plan plan, String resourceName, String value, Instant begint, Instant cutofft, ArrayList<Instant> windows) {
        return okToSchedule(plan, resourceName, SerializedValue.of(value), duration(1, SECOND), durationBetweenInstants(begint, cutofft).plus(duration(24, HOURS)), windows);
    }

    public static Instant okToSchedule(Plan plan, String resourceName, SerializedValue value, Duration minDuration, Duration maxDuration, ArrayList<Instant> mywindows) {
        Instant result = null;
        for (final var segment : plan.resource(resourceName, gov.nasa.jpl.aerie.timeline.collections.profiles.Constants::deserialize)
                .filterByDuration(new Interval($(minDuration), $(maxDuration), Inclusive, Inclusive))
                .collect(plan.totalBounds())) {
            if (segment.getValue().equals(value)) {
                var start = plan.toAbsolute(segment.getInterval().getStart());
                var end = plan.toAbsolute(segment.getInterval().getEnd());
                if (result == null) result = start;
                mywindows.add(start);
                mywindows.add(end);
            }
        }
        return result;
    }

    public static boolean booleanValueAt(Plan plan, String resourceName, Instant time) {
        return valueAt(plan, resourceName, time).asBoolean().get();
    }

    public static String stringValueAt(Plan plan, String resourceName, Instant time) {
        return valueAt(plan, resourceName, time).asString().get();
    }

    private static SerializedValue valueAt(Plan plan, String resourceName, Instant instant) {
        if (!plan.totalBounds().contains(plan.toRelative(instant)))
            throw new IllegalArgumentException("Requested time is out of bounds: " + instant);
        final var resource = plan.resource(resourceName, gov.nasa.jpl.aerie.timeline.collections.profiles.Constants::deserialize);
        final var collected = resource.collect(new Interval(plan.toRelative(instant), plan.toRelative(instant), Inclusive, Inclusive));
        return collected.get(0).getValue();
    }
}
