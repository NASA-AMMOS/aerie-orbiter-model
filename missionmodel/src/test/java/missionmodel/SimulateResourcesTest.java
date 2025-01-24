package missionmodel;

import gov.nasa.jpl.aerie.merlin.driver.*;
import gov.nasa.jpl.aerie.types.ActivityDirective;
import gov.nasa.jpl.aerie.types.ActivityDirectiveId;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import missionmodel.generated.GeneratedModelType;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.HOURS;

public class SimulateResourcesTest {

  @Test
  void testSimulation() {
    final var simulationStartTime = Instant.parse("2033-09-15T00:00:00Z");;
    final var simulationDuration = Duration.of(24, HOURS);

    // Input configuration
    //final Integer scid = -660; // VERITAS
    final Configuration geomConfig = Configuration.defaultConfiguration();

    // Add Activities to Plan
    final Map<ActivityDirectiveId, ActivityDirective> schedule = new HashMap<>();

//        schedule.put(new ActivityDirectiveId(1L), new ActivityDirective(
//                Duration.ZERO,
//                "GncChangeControlMode",
//                Map.of("gncControlMode", SerializedValue.of("THRUSTERS")),
//                null,
//                true
//        ));

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

