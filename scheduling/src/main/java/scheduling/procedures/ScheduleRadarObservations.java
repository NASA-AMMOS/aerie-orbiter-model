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
import gov.nasa.jpl.time.Time;
import missionmodel.JPLTimeConvertUtility;
import missionmodel.geometry.directspicecalls.SpiceDirectEventGenerator;
import missionmodel.geometry.interfaces.GeometryInformationNotAvailableException;
import missionmodel.geometry.resources.EclipseTypes;
import missionmodel.geometry.spiceinterpolation.Bodies;
import missionmodel.radar.RadarDataCollectionMode;
import missionmodel.spice.Spice;
import org.apache.commons.lang3.tuple.Pair;
import spice.basic.SpiceErrorException;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static scheduling.SchedulingConstants.*;
import static scheduling.SchedulingUtils.*;

@SchedulingProcedure
public record ScheduleRadarObservations() implements Goal {

    @Override
    public void run(EditablePlan plan) {

      Instant planEnd = plan.toAbsolute(plan.totalBounds().end);

      // Find Periapsis Activities
      var periapsisActs = plan.directives("Periapsis").collect();

      // Create orbit windows based on periapsis times
      // Note we are ignoring any partial orbit before first periapsis
      ArrayList<Pair<Instant,Instant>> windows= new ArrayList<>();
      for (int i = 0; i < periapsisActs.size(); i=i+2) {
        Instant start = plan.toAbsolute(periapsisActs.get(i).getStartTime());
        Instant end;
        if (i + 1 < periapsisActs.size()) {
          end = plan.toAbsolute(periapsisActs.get(i+1).getStartTime());
        } else {
          end = planEnd;
        }
        windows.add(Pair.of(start, end));
      }

      // For the first 11 orbits, schedule VISAR observations with the following per-orbit conops
      // 50% DEM, 37.5% MedRes and 12.5% HiRes
      // Turn VISAR On 3 hours before first observation (warmup time assumption)
      long VISAR_ORBIT_SEGMENTS = 8;

      Map<String, SerializedValue> actArgs = Map.of();

      boolean firstObs = true;
      for(int i = 0; i < windows.size(); i++){
        int SciOrDl =  i % (NUM_SCI_ORBITS + NUM_DL_ORBITS);
        if (SciOrDl < NUM_SCI_ORBITS) {
          Instant orbStartTime = windows.get(i).getLeft();

          // Turn on VISAR and warmup before first observation
          if (firstObs) {
            firstObs = false;
            Instant warmupTime = instantMinusDuration(orbStartTime, RADAR_WARMUP_DUR);

            // Create new activity
            var newDirective = new NewDirective(
              new AnyDirective(Map.of()),
              "Radar_On",
              "Radar_On",
              new DirectiveStart.Absolute(plan.toRelative( warmupTime )));
            plan.create(newDirective);
          }

          // No need to do anything if this is the last orbit
          if (i+1 != windows.size()) {
            Instant nexOrbStartTime = windows.get(i+1).getLeft();
            Duration orbSegDur = durationBetweenInstants(orbStartTime, nexOrbStartTime).dividedBy(VISAR_ORBIT_SEGMENTS);
            Instant nextRadarActTime = orbStartTime;
            // The first four segments should have DEM on
            actArgs = Map.of(
              "mode", new EnumValueMapper<>(RadarDataCollectionMode.class).serializeValue(RadarDataCollectionMode.LOW_RES));
            var newDirective = new NewDirective(
              new AnyDirective(actArgs),
              "ChangeRadarDataMode",
              "ChangeRadarDataMode",
              new DirectiveStart.Absolute(plan.toRelative( nextRadarActTime )));
            plan.create(newDirective);
            nextRadarActTime = instantPlusDuration(nextRadarActTime, orbSegDur.times(4));

            // The next 3 segments will be MedRes
            actArgs = Map.of(
              "mode", new EnumValueMapper<>(RadarDataCollectionMode.class).serializeValue(RadarDataCollectionMode.MED_RES));
            newDirective = new NewDirective(
              new AnyDirective(actArgs),
              "ChangeRadarDataMode",
              "ChangeRadarDataMode",
              new DirectiveStart.Absolute(plan.toRelative( nextRadarActTime )));
            plan.create(newDirective);
            nextRadarActTime = instantPlusDuration(nextRadarActTime, orbSegDur.times(3));

            // The final segment will be HiRes
            actArgs = Map.of(
              "mode", new EnumValueMapper<>(RadarDataCollectionMode.class).serializeValue(RadarDataCollectionMode.HI_RES));
            newDirective = new NewDirective(
              new AnyDirective(actArgs),
              "ChangeRadarDataMode",
              "ChangeRadarDataMode",
              new DirectiveStart.Absolute(plan.toRelative( nextRadarActTime )));
            plan.create(newDirective);
            nextRadarActTime = instantPlusDuration(nextRadarActTime, orbSegDur.times(1));

            // Turn VISAR off if this is the last science orbit
            if (SciOrDl + 1 == NUM_SCI_ORBITS) {
              actArgs = Map.of(
                "mode", new EnumValueMapper<>(RadarDataCollectionMode.class).serializeValue(RadarDataCollectionMode.OFF));
              newDirective = new NewDirective(
                new AnyDirective(actArgs),
                "ChangeRadarDataMode",
                "ChangeRadarDataMode",
                new DirectiveStart.Absolute(plan.toRelative( nextRadarActTime )));
              plan.create(newDirective);

              newDirective = new NewDirective(
                new AnyDirective(Map.of()),
                "Radar_Off",
                "Radar_Off",
                new DirectiveStart.Absolute(plan.toRelative( nextRadarActTime )));

            }
          }

        }
      }

      // Actually add activities to the plan
      plan.commit();

    }
}
