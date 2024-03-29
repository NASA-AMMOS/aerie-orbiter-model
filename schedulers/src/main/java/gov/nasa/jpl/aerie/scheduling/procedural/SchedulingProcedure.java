package gov.nasa.jpl.aerie.scheduling.procedural;

import gov.nasa.jpl.aerie.timeline.plan.Plan;
import missionmodel.telecom.Downlink;

import java.time.Instant;

public interface SchedulingProcedure {
    void procedure(Instant begint, Instant cutofft, Plan plan, PlanManipulator planManipulator) throws Exception;
    interface PlanManipulator {
        void addActivity(Instant startTime, Downlink activity);
    }
}
