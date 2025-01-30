package scheduling.procedures;

import gov.nasa.ammos.aerie.procedural.scheduling.Goal;
import gov.nasa.ammos.aerie.procedural.scheduling.annotations.SchedulingProcedure;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.EditablePlan;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.NewDirective;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.AnyDirective;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.DirectiveStart;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.DurationValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.EnumValueMapper;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.time.Time;
import missionmodel.JPLTimeConvertUtility;
import missionmodel.Window;
import missionmodel.geometry.activities.atomic.EnterOccultation;
import missionmodel.geometry.activities.atomic.ExitOccultation;
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

import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;
import static missionmodel.generated.ActivityActions.spawn;

@SchedulingProcedure
public record AddOccultations(
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

      // Compute occultations
      List<Window> occultationTimes;
      try {
        // we are looking from the observer (e.g. a DSN station) and treating the target spacecraft as a point source,
        // and so a 'partial' is not meaningful
        occultationTimes = generator.getOccultations( planStartJplTime, planEndJplTime,
          JPLTimeConvertUtility.getJplTimeDur(stepSize), observer, target, occultingBody,"CN", true, true, useDSK);
      } catch (GeometryInformationNotAvailableException e) {
        occultationTimes = new ArrayList<>();
      }

      // There may be some occultations that are before the start of this plan. Remove them or alter the start time to start
      // at the start time of the plan
      while (!occultationTimes.isEmpty() && occultationTimes.getFirst().getStart().lessThan(planStartJplTime)) {
        if (occultationTimes.getFirst().getEnd().lessThanOrEqualTo(planStartJplTime)) {
          occultationTimes.removeFirst();
        } else if (occultationTimes.getFirst().getStart().lessThan(planStartJplTime)) {
          occultationTimes.set(0, new Window(planStartJplTime, occultationTimes.getFirst().getEnd(), occultationTimes.getFirst().getType()) );
        }
      }

      // Assume 'target' is always the spacecraft
      Map<String, SerializedValue> actArgs = Map.of(
        "body", SerializedValue.of(occultingBody),
        "station", SerializedValue.of(observer));

      // Loop through occultations and add start/end activities when appropriate
      Duration durToSearchEnd = plan.totalBounds().duration();
      Time now = planStartJplTime;
      for(Window w : occultationTimes){
        // Don't spawn any occultations at or after the end of the search duration
        if (w.getStart().lessThan(planEndJplTime)) {
          // Wait until start time of occultation and spawn EnterOccultation
          gov.nasa.jpl.time.Duration delayTime = w.getStart().minus(now);
          now = now.plus(delayTime);

          // Create new activity
          var newDirective = new NewDirective(
            new AnyDirective(actArgs),
            "EnterOccultation_" + occultingBody,
            "EnterOccultation",
            new DirectiveStart.Absolute(plan.toRelative( now.toTimezone("UTC").toInstant()) ));
          plan.create(newDirective);

          // Wait until end time of occultation and spawn ExitOccultation
          durToSearchEnd = durToSearchEnd.minus(JPLTimeConvertUtility.getDuration(delayTime));
          delayTime = w.getEnd().minus(now);

          // Check to make sure the end of the occultation is not past the end of the search window. If it is, we are done
          // and do not need to add an exit occultation activity
          if (durToSearchEnd.minus(JPLTimeConvertUtility.getDuration(delayTime)).isPositive()) {
            now = now.plus(delayTime);
            // Assume 'target' is always the spacecraft
            // Create new activity
            newDirective = new NewDirective(
              new AnyDirective(actArgs),
              "ExitOccultation_" + occultingBody,
              "ExitOccultation",
              new DirectiveStart.Absolute(plan.toRelative( now.toTimezone("UTC").toInstant()) ));
            plan.create(newDirective);

            durToSearchEnd = durToSearchEnd.minus(JPLTimeConvertUtility.getDuration(delayTime));
          }
        }
      }
      // Actually add activities to the plan
      plan.commit();
    }

}
