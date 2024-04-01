package schedulers;

import gov.nasa.jpl.aerie.timeline.plan.Plan;
import missionmodel.telecom.Downlink;
import missionmodel.visar.ChangeVisarDataMode;
import missionmodel.visar.VISAR_Off;
import missionmodel.visar.VISAR_On;

import java.time.Instant;

public interface SchedulingProcedure {
    void procedure(Instant begint, Instant cutofft, Plan plan, PlanManipulator planManipulator) throws Exception;
    interface PlanManipulator {
        void addActivity(Instant startTime, Downlink activity);
        void addActivity(Instant startTime, VISAR_On activity);
        void addActivity(Instant startTime, VISAR_Off activity);
        void addActivity(Instant startTime, ChangeVisarDataMode activity);
    }
}
