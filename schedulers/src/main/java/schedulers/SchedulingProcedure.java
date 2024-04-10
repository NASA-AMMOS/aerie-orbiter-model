package schedulers;

import gov.nasa.jpl.aerie.timeline.plan.Plan;
import missionmodel.radar.ChangeRadarDataMode;
import missionmodel.telecom.Downlink;
import missionmodel.radar.Radar_Off;
import missionmodel.radar.Radar_On;

import java.time.Instant;

public interface SchedulingProcedure {
    void procedure(Instant begint, Instant cutofft, Plan plan, PlanManipulator planManipulator) throws Exception;
    interface PlanManipulator {
        void addActivity(Instant startTime, Downlink activity);
        void addActivity(Instant startTime, Radar_On activity);
        void addActivity(Instant startTime, Radar_Off activity);
        void addActivity(Instant startTime, ChangeRadarDataMode activity);
    }
}
