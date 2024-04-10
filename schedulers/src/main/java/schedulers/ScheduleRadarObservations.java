package schedulers;

import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.timeline.collections.profiles.Constants;
import gov.nasa.jpl.aerie.timeline.plan.Plan;
import missionmodel.radar.ChangeRadarDataMode;
import missionmodel.radar.RadarDataCollectionMode;
import missionmodel.radar.Radar_On;
import missionmodel.radar.Radar_Off;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.ArrayList;

import static schedulers.Constants.*;
import static schedulers.Utils.*;
import static schedulers.Utils.durationBetweenInstants;

public class ScheduleRadarObservations implements SchedulingProcedure {
  @Override
  public void procedure(Instant begint, Instant cutofft, Plan plan, PlanManipulator planManipulator) {

    // Find Periapsis Times
    ArrayList<Pair<Instant,Instant>> windows= new ArrayList<>();
    for (final var segment : plan.resource("Periapsis_MARS", Constants::deserialize).collect(plan.totalBounds())) {
      if (segment.getValue().equals(SerializedValue.of(true))) {
        var start = plan.toAbsolute(segment.getInterval().getStart());
        var end = plan.toAbsolute(segment.getInterval().getEnd());
        windows.add(Pair.of(start, end));
      }
    }

    // For the first 11 orbits, schedule VISAR observations with the following per-orbit conops
    // 50% DEM, 37.5% MedRes and 12.5% HiRes
    // Turn VISAR On 3 hours before first observation (warmup time assumption)
    long VISAR_ORBIT_SEGMENTS = 8;

    boolean firstObs = true;
    for(int i = 0; i < windows.size(); i++){
        Integer SciOrDl =  i % (NUM_SCI_ORBITS + NUM_DL_ORBITS);
        if (SciOrDl < NUM_SCI_ORBITS) {
          Instant orbStartTime = windows.get(i).getLeft();
          // Turn on VISAR and warmup before first observation
          if (firstObs) {
            firstObs = false;
            Instant warmupTime = instantMinusDuration(orbStartTime, RADAR_WARMUP_DUR);
            planManipulator.addActivity( warmupTime, new Radar_On());
          }

          // No need to do anything if this is the last orbit
          if (i+1 != windows.size()) {
            Instant nexOrbStartTime = windows.get(i+1).getLeft();
            Duration orbSegDur = durationBetweenInstants(orbStartTime, nexOrbStartTime).dividedBy(VISAR_ORBIT_SEGMENTS);
            Instant nextRadarActTime = orbStartTime;
            // The first four segments should have DEM on
            planManipulator.addActivity(nextRadarActTime,
              new ChangeRadarDataMode(RadarDataCollectionMode.LOW_RES));
            nextRadarActTime = instantPlusDuration(nextRadarActTime, orbSegDur.times(4));
            // The next 3 segments will be MedRes
            planManipulator.addActivity(nextRadarActTime,
              new ChangeRadarDataMode(RadarDataCollectionMode.MED_RES));
            nextRadarActTime = instantPlusDuration(nextRadarActTime, orbSegDur.times(3));
            // The final segment will be HiRes
            planManipulator.addActivity(nextRadarActTime,
              new ChangeRadarDataMode(RadarDataCollectionMode.HI_RES));
            nextRadarActTime = instantPlusDuration(nextRadarActTime, orbSegDur.times(1));
            // Turn VISAR off if this is the last science orbit
            if (SciOrDl + 1 == NUM_SCI_ORBITS) {
              planManipulator.addActivity(nextRadarActTime,  new ChangeRadarDataMode(RadarDataCollectionMode.OFF));
              planManipulator.addActivity(nextRadarActTime, new Radar_Off());
            }
          }

        }
    }

//        plan.markStale("/geometric")
  }
}
