package scheduling.procedures;

import gov.nasa.ammos.aerie.procedural.scheduling.Goal;
import gov.nasa.ammos.aerie.procedural.scheduling.annotations.SchedulingProcedure;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.EditablePlan;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.NewDirective;
import gov.nasa.ammos.aerie.procedural.timeline.Interval;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.AnyDirective;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.DirectiveStart;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.EnumValueMapper;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import missionmodel.radar.RadarDataCollectionMode;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import static scheduling.SchedulingConstants.*;
import static scheduling.SchedulingUtils.*;

@SchedulingProcedure
public record ScheduleFilterData() implements Goal {

    @Override
    public void run(EditablePlan plan) {
      // Find Radar Observations
      var radarActs = plan.directives("ChangeRadarDataMode").collect();
      for(var r: radarActs) {
        // Create new activity
        var newDirective = new NewDirective(
                new AnyDirective(Map.of("percent", SerializedValue.of(0.45))),
                "FilterData",
                "FilterData",
                new DirectiveStart.Anchor(r.id, Duration.MINUTE, DirectiveStart.Anchor.AnchorPoint.End));
        plan.create(newDirective);
      }

      // Actually add activities to the plan
      plan.commit();
    }
}
