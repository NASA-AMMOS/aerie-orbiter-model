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
import missionmodel.Window;
import missionmodel.geometry.directspicecalls.SpiceDirectEventGenerator;
import missionmodel.geometry.interfaces.GeometryInformationNotAvailableException;
import missionmodel.geometry.resources.EclipseTypes;
import missionmodel.geometry.spiceinterpolation.Bodies;
import missionmodel.spice.Spice;
import spice.basic.SpiceErrorException;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SchedulingProcedure
public record AddSpacecraftEclipses(
        String observer,
        String target,
        String occultingBody,
        Duration stepSize,
        Boolean useDSK ) implements Goal {

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

      // Initialize Geometry Bodies and Generator
      Bodies bodiesObj = new Bodies();
      SpiceDirectEventGenerator generator = new SpiceDirectEventGenerator(bodiesObj.getBodiesMap());

      // Get useful plan bounds information
      Instant planStart = plan.toAbsolute(plan.totalBounds().start);
      Time planStartJplTime = JPLTimeConvertUtility.jplTimeFromUTCInstant(planStart);
      Instant planEnd = plan.toAbsolute(plan.totalBounds().end);
      Time planEndJplTime = JPLTimeConvertUtility.jplTimeFromUTCInstant(planEnd);

      // Compute eclipses
      List<Window> eclipses;
      try {
        // false, false because if a DSK is not used, we want an ellipsoid instead of a point source as eclipsing body,
        // and we don't want to merge partials
        eclipses = generator.getOccultations( planStartJplTime,
          planEndJplTime,
          JPLTimeConvertUtility.getJplTimeDur(stepSize), observer, target, occultingBody,"CN", false, false, useDSK);
      } catch (GeometryInformationNotAvailableException e) {
        eclipses = new ArrayList<>();
      }

      // There may be some eclipses that are before the start of this plan. Remove them or alter the start time to start
      // at the start time of the plan
      while (!eclipses.isEmpty() && eclipses.getFirst().getStart().lessThan(planStartJplTime)) {
        if (eclipses.getFirst().getEnd().lessThanOrEqualTo(planStartJplTime)) {
          eclipses.removeFirst();
        } else if (eclipses.getFirst().getStart().lessThan(planStartJplTime)) {
          eclipses.set(0, new Window(planStartJplTime, eclipses.getFirst().getEnd(), eclipses.getFirst().getType()) );
        }
      }

      // Loop through eclipses and add start/end activities when appropriate
      Duration durToSearchEnd = plan.totalBounds().duration();
      Time now = planStartJplTime;
      for(int i = 0; i < eclipses.size(); i++){

        // Don't spawn any eclipses at or after the end of the search duration
        if (eclipses.get(i).getStart().lessThan(planEndJplTime)) {
          gov.nasa.jpl.time.Duration delayTime = eclipses.get(i).getStart().minus(now);
          now = now.plus(delayTime);

          Map<String, SerializedValue> actArgs = Map.of("body", SerializedValue.of(occultingBody),
            "type", new EnumValueMapper<>(EclipseTypes.class).serializeValue(EclipseTypes.valueOf(eclipses.get(i).getType())),
            "duration", new DurationValueMapper().serializeValue( JPLTimeConvertUtility.getDuration(eclipses.get(i).getDuration())));

          // Create new activity
          var newDirective = new NewDirective(
            new AnyDirective(actArgs),
            "SpacecraftEnterEclipse_" + occultingBody,
            "SpacecraftEnterEclipse",
            new DirectiveStart.Absolute(plan.toRelative( now.toTimezone("UTC").toInstant()) ));
          plan.create(newDirective);

          durToSearchEnd = durToSearchEnd.minus(JPLTimeConvertUtility.getDuration(delayTime));

          // while we always enter an eclipse, we don't always exit - we could transition from full to partial eclipse
          if (i == eclipses.size() - 1 ||
            eclipses.get(i).getEnd().absoluteDifference(eclipses.get(i + 1).getStart()).greaterThan(
              JPLTimeConvertUtility.getJplTimeDur(Duration.SECOND))) {
            delayTime = eclipses.get(i).getEnd().minus(now);

            // Check to make sure the end of the eclipse is not past the end of the search window. If it is, we are done
            // and do not need to add an exit eclipse activity
            if (durToSearchEnd.minus( JPLTimeConvertUtility.getDuration(delayTime)).isPositive()) {
              now = now.plus(delayTime);

              actArgs = Map.of("body", SerializedValue.of(occultingBody));
              newDirective = new NewDirective(
                new AnyDirective(actArgs),
                "SpacecraftExitEclipse_" + occultingBody,
                "SpacecraftExitEclipse",
                new DirectiveStart.Absolute(plan.toRelative( now.toTimezone("UTC").toInstant())));
              plan.create(newDirective);

              durToSearchEnd = durToSearchEnd.minus( JPLTimeConvertUtility.getDuration(delayTime) );
            }
          }
        }
      }
      // Actually add activities to the plan
      plan.commit();
    }

}
