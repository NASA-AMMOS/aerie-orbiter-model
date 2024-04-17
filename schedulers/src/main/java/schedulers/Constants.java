package schedulers;

import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

import java.time.Instant;

public class Constants {
    public static final Instant J2000 = Instant.parse("2000-01-01T00:00:00Z");
    public static final Integer NUM_SCI_ORBITS = 9;
    public static final Integer NUM_DL_ORBITS = 5;
    public static final Duration RADAR_WARMUP_DUR = Duration.of(15, Duration.MINUTE);
    public static final Duration DL_BUFFER_DUR = Duration.of(1, Duration.MINUTE);

}
