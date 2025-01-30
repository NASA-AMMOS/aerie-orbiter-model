package scheduling.procedures;

import gov.nasa.ammos.aerie.procedural.scheduling.Goal;
import gov.nasa.ammos.aerie.procedural.scheduling.annotations.SchedulingProcedure;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.EditablePlan;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.NewDirective;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.AnyDirective;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.DirectiveStart;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import missionmodel.JPLTimeConvertUtility;
import missionmodel.geometry.directspicecalls.SpiceDirectEventGenerator;
import missionmodel.geometry.interfaces.GeometryInformationNotAvailableException;
import missionmodel.geometry.spiceinterpolation.Bodies;
import missionmodel.spice.Spice;
import spice.basic.SpiceErrorException;

import gov.nasa.jpl.time.Time;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SchedulingProcedure
public record AddPeriapses(
        String body,
        String target,
        Duration stepSize,
        double maxDistanceFilter ) implements Goal {

    public static final Path VERSIONED_KERNELS_ROOT_DIRECTORY = Path.of(System.getenv().getOrDefault("SPICE_DIRECTORY", "spice/kernels"));

    public static final String NAIF_META_KERNEL_PATH = VERSIONED_KERNELS_ROOT_DIRECTORY.toString() + "/latest_meta_kernel.tm";

    @Override
    public void run(EditablePlan plan) {

      // Instantiate Spice
      try {
        Spice.initialize(NAIF_META_KERNEL_PATH);
      } catch (SpiceErrorException e) {
        System.out.println(e.getMessage());
      }

      // Initialize Geometry Bodies
      Bodies bodiesObj = new Bodies();
      SpiceDirectEventGenerator generator = new SpiceDirectEventGenerator(bodiesObj.getBodiesMap());
      Instant planStart = plan.toAbsolute(plan.totalBounds().start);
      Instant planEnd = plan.toAbsolute(plan.totalBounds().end);

      List<Time> periapsisTimes;

      Map<String, SerializedValue> actArgs = Map.of("body", SerializedValue.of(target));

      try {
        periapsisTimes = generator.getPeriapses(JPLTimeConvertUtility.jplTimeFromUTCInstant(planStart),
          JPLTimeConvertUtility.jplTimeFromUTCInstant(planEnd),
          JPLTimeConvertUtility.getJplTimeDur(stepSize), body, target, maxDistanceFilter, "NONE");
      } catch (GeometryInformationNotAvailableException e) {
        periapsisTimes = new ArrayList<>();
      }

      for (Time periapsisTime : periapsisTimes) {

        // Create new activity
        var newDirective = new NewDirective(
          new AnyDirective(actArgs),
          "Periapsis_" + target,
          "Periapsis",
          new DirectiveStart.Absolute(plan.toRelative( periapsisTime.toTimezone("UTC").toInstant())));
        plan.create(newDirective);

      }

      // Actually add activities to the plan
      plan.commit();

    }
}
