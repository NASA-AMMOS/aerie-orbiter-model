package schedulers;

import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.timeline.collections.profiles.Numbers;
import gov.nasa.jpl.aerie.timeline.collections.profiles.Constants;
import gov.nasa.jpl.aerie.timeline.payloads.Segment;
import gov.nasa.jpl.aerie.timeline.plan.Plan;
import missionmodel.telecom.Downlink;
import missionmodel.visar.ChangeVisarDataMode;
import missionmodel.visar.VISAR_Off;
import missionmodel.visar.VISAR_On;
import missionmodel.visar.VisarDataCollectionMode;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.*;
import static schedulers.Constants.*;
import static schedulers.Utils.*;
import static schedulers.Utils.durationBetweenInstants;

public class ScheduleDemObservations implements SchedulingProcedure {
  @Override
  public void procedure(Instant begint, Instant cutofft, Plan plan, PlanManipulator planManipulator) {

    // Find Periapsis Times
    ArrayList<Pair<Instant,Instant>> windows= new ArrayList<>();
    for (final var segment : plan.resource("Periapsis_VENUS", Constants::deserialize).collect(plan.totalBounds())) {
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
            Instant warmupTime = instantMinusDuration(orbStartTime, VISAR_WARMUP_DUR);
            planManipulator.addActivity( warmupTime, new VISAR_On());
          }

          // No need to do anything if this is the last orbit
          if (i+1 != windows.size()) {
            Instant nexOrbStartTime = windows.get(i+1).getLeft();
            Duration orbSegDur = durationBetweenInstants(orbStartTime, nexOrbStartTime).dividedBy(VISAR_ORBIT_SEGMENTS);
            Instant nextVisarActTime = orbStartTime;
            // The first four segments should have DEM on
            planManipulator.addActivity( nextVisarActTime ,
              new ChangeVisarDataMode(VisarDataCollectionMode.DEM));
            nextVisarActTime = instantPlusDuration(nextVisarActTime, orbSegDur.times(4));
            // The next 3 segments will be MedRes
            planManipulator.addActivity( nextVisarActTime ,
              new ChangeVisarDataMode(VisarDataCollectionMode.MED_RES));
            nextVisarActTime = instantPlusDuration(nextVisarActTime, orbSegDur.times(3));
            // The final segment will be HiRes
            planManipulator.addActivity( nextVisarActTime ,
              new ChangeVisarDataMode(VisarDataCollectionMode.HI_RES));
            nextVisarActTime = instantPlusDuration(nextVisarActTime, orbSegDur.times(1));
            // Turn VISAR off if this is the last science orbit
            if (SciOrDl + 1 == NUM_SCI_ORBITS) {
              planManipulator.addActivity( nextVisarActTime,  new ChangeVisarDataMode(VisarDataCollectionMode.OFF));
              planManipulator.addActivity( nextVisarActTime, new VISAR_Off());
            }
          }

        }
    }

//        plan.markStale("/geometric")
  }
}
