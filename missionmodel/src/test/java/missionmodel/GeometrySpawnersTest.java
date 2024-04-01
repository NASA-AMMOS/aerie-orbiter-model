package missionmodel;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import missionmodel.generated.GeneratedModelType;
import gov.nasa.jpl.aerie.merlin.driver.ActivityDirective;
import gov.nasa.jpl.aerie.merlin.driver.ActivityDirectiveId;
import gov.nasa.jpl.aerie.merlin.driver.DirectiveTypeRegistry;
import gov.nasa.jpl.aerie.merlin.driver.MissionModel;
import gov.nasa.jpl.aerie.merlin.driver.MissionModelBuilder;
import gov.nasa.jpl.aerie.merlin.driver.SimulationDriver;
import gov.nasa.jpl.aerie.merlin.driver.SimulationResults;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.HOURS;

public class GeometrySpawnersTest {

  @Test
  void testGeometrySpawners() {
    final var simulationStartTime = Instant.parse("2033-09-17T00:00:00Z");;
    final var simulationDuration = Duration.of(24, HOURS);

    // Input configuration
    // final Integer scid = -660; // VERITAS
    final Configuration geomConfig = Configuration.defaultConfiguration();

    // Add Activities to Plan
    final Map<ActivityDirectiveId, ActivityDirective> schedule = new HashMap<>();

    // AddSpacecraftEclipses spawner
    schedule.put(new ActivityDirectiveId(1L), new ActivityDirective(
      Duration.ZERO,
      "AddSpacecraftEclipses",
      Map.of("searchDuration", SerializedValue.of(86400000000L),
        "observer", SerializedValue.of("-660"),
        "target", SerializedValue.of("SUN"),
        "occultingBody", SerializedValue.of("VENUS"),
        "stepSize", SerializedValue.of(1800000000L),
        "useDSK", SerializedValue.of(false)),
      null,
      true
    ));

    // AddOccultations spawner
    schedule.put(new ActivityDirectiveId(1L), new ActivityDirective(
      Duration.ZERO,
      "AddOccultations",
      Map.of("searchDuration", SerializedValue.of(86400000000L),
        "observer", SerializedValue.of("DSS-24"),
        "target", SerializedValue.of("-660"),
        "occultingBody", SerializedValue.of("VENUS"),
        "stepSize", SerializedValue.of(1800000000L),
        "useDSK", SerializedValue.of(false)),
      null,
      true
    ));

    // AddPeriapsis spawner
    schedule.put(new ActivityDirectiveId(1L), new ActivityDirective(
      Duration.ZERO,
      "AddPeriapsis",
      Map.of("searchDuration", SerializedValue.of(86400000000L),
        "body", SerializedValue.of("-660"),
        "target", SerializedValue.of("VENUS"),
        "stepSize", SerializedValue.of(1800000000L),
        "maxDistanceFilter", SerializedValue.of(10000.0)),
      null,
      true
    ));

    final var results = simulate(geomConfig, simulationStartTime, simulationDuration, schedule);
  }

  public SimulationResults simulate(
    Configuration configuration,
    Instant simulationStartTime,
    Duration simulationDuration,
    Map<ActivityDirectiveId, ActivityDirective> schedule
  ) {
    return SimulationDriver.simulate(
      makeMissionModel(new MissionModelBuilder(), simulationStartTime, configuration),
      schedule,
      simulationStartTime,
      simulationDuration,
      simulationStartTime,
      simulationDuration,
      () -> { return false; }
    );
  }

  private static MissionModel<?> makeMissionModel(final MissionModelBuilder builder, final Instant planStart, final Configuration config) {
    final var factory = new GeneratedModelType();
    final var registry = DirectiveTypeRegistry.extract(factory);
    final var model = factory.instantiate(planStart, config, builder);
    return builder.build(model, registry);
  }
}
