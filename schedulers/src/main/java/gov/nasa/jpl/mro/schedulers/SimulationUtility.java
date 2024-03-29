package gov.nasa.jpl.mro.schedulers;

import gov.nasa.jpl.aerie.merlin.driver.*;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import missionmodel.Configuration;
import missionmodel.generated.GeneratedModelType;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class SimulationUtility {
    private static MissionModel<?> makeMissionModel(final MissionModelBuilder builder, final Instant planStart, final Configuration config) {
        final var factory = new GeneratedModelType();
        final var registry = DirectiveTypeRegistry.extract(factory);
        // TODO: [AERIE-1516] Teardown the model to release any system resources (e.g. threads).
        final var model = factory.instantiate(planStart, config, builder);
        return builder.build(model, registry);
    }

    public static SimulationResults
    simulate(final Map<ActivityDirectiveId, ActivityDirective> schedule, final Instant planStart, final Duration simulationDuration) {
        final var config = Configuration.defaultConfiguration();
        final var startTime = Instant.now();
        final var missionModel = makeMissionModel(new MissionModelBuilder(), planStart, config);

        return SimulationDriver.simulate(
                missionModel,
                schedule,
                startTime,
                simulationDuration,
                startTime,
                simulationDuration,
                () -> false);
    }

    @SafeVarargs
    public static Map<ActivityDirectiveId, ActivityDirective> buildSchedule(final Pair<Duration, SerializedActivity>... activitySpecs) {
        final var schedule = new HashMap<ActivityDirectiveId, ActivityDirective>();
        long counter = 0;

        for (final var activitySpec : activitySpecs) {
            schedule.put(
                    new ActivityDirectiveId(counter++),
                    new ActivityDirective(activitySpec.getLeft(), activitySpec.getRight(), null, true));
        }

        return schedule;
    }
}
