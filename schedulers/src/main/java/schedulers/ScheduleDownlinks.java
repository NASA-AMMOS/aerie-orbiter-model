package schedulers;

import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.timeline.collections.profiles.Constants;
import gov.nasa.jpl.aerie.timeline.collections.profiles.Numbers;
import gov.nasa.jpl.aerie.timeline.payloads.Segment;
import gov.nasa.jpl.aerie.timeline.plan.Plan;
import missionmodel.telecom.Downlink;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.*;
import static schedulers.Constants.*;
import static schedulers.Utils.*;

public class ScheduleDownlinks implements SchedulingProcedure {
    @Override
    public void procedure(Instant begint, Instant cutofft, Plan plan, PlanManipulator planManipulator) {
      //        plan.isStale("/geometric")
      // Find intervals where there are NOT occultations
      Numbers<Number> resource = plan.resource("Occultation", Numbers::deserialize);
      ArrayList<Pair<Instant,Instant>> windows= new ArrayList<>();
      for (final var segment : resource.greaterThanOrEqualTo(new Numbers<>(1.0)).collect(plan.totalBounds())) {
        if (segment.component2().equals(false)) {
          var start = plan.toAbsolute(segment.getInterval().start);
          var end = plan.toAbsolute(segment.getInterval().end);
          windows.add(Pair.of(start, end));
        }
      }

      // Find Periapsis Times
      ArrayList<Pair<Instant,Instant>> periapsisWindows= new ArrayList<>();
      for (final var segment : plan.resource("Periapsis_MARS", Constants::deserialize).collect(plan.totalBounds())) {
        if (segment.component2().equals(SerializedValue.of(true))) {
          var start = plan.toAbsolute(segment.getInterval().start);
          var end = plan.toAbsolute(segment.getInterval().end);
          periapsisWindows.add(Pair.of(start, end));
        }
      }

      // Only schedule downlinks on DL orbits
      for(int i = 0; i < periapsisWindows.size(); i++){
        Integer SciOrDl =  i % (NUM_SCI_ORBITS + NUM_DL_ORBITS);
        if (SciOrDl >= NUM_SCI_ORBITS) {
          Instant orbStartTime = periapsisWindows.get(i).getLeft();
          // Search through all non-occultation windows and find ones that are within this orbit
          for (Pair<Instant,Instant> window: windows) {
            Instant nexOrbStartTime = cutofft;
            if (i + 1 != periapsisWindows.size()) {
              nexOrbStartTime = periapsisWindows.get(i + 1).getLeft();
            }
            // Schedule downlinks for those within this orbit
            if (window.getLeft().isAfter(orbStartTime) && window.getLeft().isBefore(nexOrbStartTime)) {
              Instant dlStart = instantPlusDuration( window.getLeft(), DL_BUFFER_DUR);
              Instant dlEnd = instantMinusDuration( window.getRight(), DL_BUFFER_DUR);
              planManipulator.addActivity( dlStart,
                new Downlink(durationBetweenInstants(dlStart, dlEnd), 1000));
            }
          }
        }
      }
//        plan.markStale("/geometric")
    }
}
