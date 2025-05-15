package scheduling.procedures;

import gov.nasa.ammos.aerie.procedural.scheduling.Goal;
import gov.nasa.ammos.aerie.procedural.scheduling.annotations.SchedulingProcedure;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.EditablePlan;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.NewDirective;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.AnyDirective;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.DirectiveStart;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.DurationValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.EnumValueMapper;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import missionmodel.radar.RadarDataCollectionMode;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import static scheduling.SchedulingConstants.*;
import static scheduling.SchedulingUtils.*;

@SchedulingProcedure
public record ScheduleDownlinks() implements Goal {

    @Override
    public void run(EditablePlan plan) {

      // Get useful plan bounds information
      Instant planStart = plan.toAbsolute(plan.totalBounds().start);
      Instant planEnd = plan.toAbsolute(plan.totalBounds().end);

      // Get enter occultation acts
      var enterOccActs = plan.directives("EnterOccultation").collect();

      // Get exit occultation acts
      var exitOccActs = plan.directives("ExitOccultation").collect();

      // Create occultation windows based on enter/exit times of occultations
      ArrayList<Pair<Instant,Instant>> occWindows= new ArrayList<>();

      // If there is an exit occultation before the first enter occultation, that occultation begins at plan start
      if ( exitOccActs.getFirst().getStartTime().shorterThan(enterOccActs.getFirst().getStartTime()) ) {
        occWindows.add(Pair.of(planStart, plan.toAbsolute( exitOccActs.getFirst().getStartTime())));
      }
      for (var enterOcc : enterOccActs) {
        Duration occStartTime = enterOcc.getStartTime();
        for (var exitOcc : exitOccActs) {
          // Look for the first time the exit occ is after this start occ time
          if (exitOcc.getStartTime().longerThan(occStartTime)) {
              occWindows.add(Pair.of(plan.toAbsolute(occStartTime), plan.toAbsolute( exitOcc.getStartTime())));
              break;
          }
        }
      }
      // If there is no exit occultation after the last enter occ, that occultation end at plan end
      if ( enterOccActs.getLast().getStartTime().longerThan(exitOccActs.getLast().getStartTime()) ) {
        occWindows.add(Pair.of(plan.toAbsolute( enterOccActs.getFirst().getStartTime()), planEnd));
      }

      // Create non-occultation windows based on occ windows
      ArrayList<Pair<Instant,Instant>> nonOccWindows = new ArrayList<>();
      for (int i = 0; i < occWindows.size(); i++) {
        if (i == 0) {
          if (!planStart.equals(occWindows.get(i).getLeft())) {
            nonOccWindows.add(Pair.of(planStart, occWindows.get(i).getLeft()));
          }
        }
        else {
          nonOccWindows.add(Pair.of( occWindows.get(i-1).getRight(), occWindows.get(i).getLeft() ));
        }

        if (i == occWindows.size() - 1)  {
          if (!occWindows.get(i).getRight().equals(planEnd)) {
            nonOccWindows.add(Pair.of(occWindows.get(i).getRight(), planEnd));
          }
        }
      }

      // Find Periapsis Activities
      var periapsisActs = plan.directives("Periapsis").collect();

      // Create orbit windows based on periapsis times
      // Note we are ignoring any partial orbit before first periapsis
      ArrayList<Pair<Instant,Instant>> periapsisWindows= new ArrayList<>();
      for (int i = 0; i < periapsisActs.size(); i=i+1) {
        Instant start = plan.toAbsolute(periapsisActs.get(i).getStartTime());
        Instant end;
        if (i + 1 < periapsisActs.size()) {
          end = plan.toAbsolute(periapsisActs.get(i+1).getStartTime());
        } else {
          end = planEnd;
        }
        periapsisWindows.add(Pair.of(start, end));
      }

      // Only schedule downlinks on DL orbits
      for(int i = 0; i < periapsisWindows.size(); i++){
        int SciOrDl =  i % (NUM_SCI_ORBITS + NUM_DL_ORBITS);
        if (SciOrDl >= NUM_SCI_ORBITS) {
          Instant orbStartTime = periapsisWindows.get(i).getLeft();
          // Search through all non-occultation windows and find ones that are within this orbit
          for (Pair<Instant,Instant> window: nonOccWindows) {
            Instant nexOrbStartTime = planEnd;
            if (i + 1 != periapsisWindows.size()) {
              nexOrbStartTime = periapsisWindows.get(i + 1).getLeft();
            }
            // Schedule downlinks for those within this orbit
            if (window.getLeft().isAfter(orbStartTime) && window.getLeft().isBefore(nexOrbStartTime)) {
              Instant dlStart = instantPlusDuration( window.getLeft(), DL_BUFFER_DUR);
              Instant dlEnd = instantMinusDuration( window.getRight(), DL_BUFFER_DUR);

              Map<String, SerializedValue> actArgs = Map.of(
                "duration", new DurationValueMapper().serializeValue(durationBetweenInstants(dlStart, dlEnd)),
                "bitRate", SerializedValue.of(1000));
              var newDirective = new NewDirective(
                new AnyDirective(actArgs),
                "Downlink",
                "Downlink",
                new DirectiveStart.Absolute(plan.toRelative( dlStart )));
              plan.create(newDirective);

            }
          }
        }
      }

      // Actually add activities to the plan
      plan.commit();

    }
}
