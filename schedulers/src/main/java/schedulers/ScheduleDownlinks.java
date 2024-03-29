package schedulers;

import gov.nasa.jpl.aerie.scheduling.procedural.SchedulingProcedure;
import gov.nasa.jpl.aerie.timeline.collections.profiles.Numbers;
import gov.nasa.jpl.aerie.timeline.payloads.Segment;
import gov.nasa.jpl.aerie.timeline.plan.Plan;
import missionmodel.telecom.Downlink;

import java.time.Instant;
import java.util.List;

public class ScheduleDownlinks implements SchedulingProcedure {
    @Override
    public void procedure(Instant begint, Instant cutofft, Plan plan, PlanManipulator planManipulator) {
        Numbers<Number> resource = plan.resource("/geometric", Numbers::deserialize);
        final var windows = resource.greaterThan(new Numbers<>(2.0)).and(resource.lessThan(new Numbers<>(10.0))).collect(plan.totalBounds());

        List<Segment<Boolean>> segments = windows;
        for (final Segment<Boolean> window : windows) {
            if (window.getValue().booleanValue() == false) continue;

            window.getInterval().getStart();
            window.getInterval().getEnd();
        }


        planManipulator.addActivity(begint, new Downlink());
    }
}
