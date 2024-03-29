package schedulers;

import gov.nasa.jpl.aerie.timeline.collections.profiles.Numbers;
import gov.nasa.jpl.aerie.timeline.payloads.Segment;
import gov.nasa.jpl.aerie.timeline.plan.Plan;
import missionmodel.telecom.Downlink;

import java.time.Instant;
import java.util.List;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.*;
import static schedulers.Utils.instantPlusDuration;

public class ScheduleDownlinks implements SchedulingProcedure {
    @Override
    public void procedure(Instant begint, Instant cutofft, Plan plan, PlanManipulator planManipulator) {
//        plan.isStale("/geometric")

        // Reconcile the current state of the plan/simulation with what this goal
        // would like to assume

//        Numbers<Number> resource = plan.resource("/geometric", Numbers::deserialize);
//        final var windows = resource.greaterThan(new Numbers<>(2.0)).and(resource.lessThan(new Numbers<>(10.0))).collect(plan.totalBounds());

//        List<Segment<Boolean>> segments = windows;
//        for (final Segment<Boolean> window : windows) {
//            if (window.getValue().booleanValue() == false) continue;
//
//            window.getInterval().getStart();
//            window.getInterval().getEnd();
//        }

        planManipulator.addActivity(instantPlusDuration(begint, duration(3, HOURS)), new Downlink(duration(1, HOUR), 12));

//        plan.markStale("/geometric")
    }
}
