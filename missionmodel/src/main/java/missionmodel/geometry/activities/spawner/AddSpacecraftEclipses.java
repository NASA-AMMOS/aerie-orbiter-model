package missionmodel.geometry.activities.spawner;

import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.time.Time;
import missionmodel.JPLTimeConvertUtility;
import missionmodel.Mission;
import missionmodel.Window;
import missionmodel.geometry.activities.atomic.SpacecraftEnterEclipse;
import missionmodel.geometry.activities.atomic.SpacecraftExitEclipse;
import missionmodel.geometry.directspicecalls.SpiceDirectEventGenerator;
import missionmodel.geometry.interfaces.GeometryInformationNotAvailableException;
import missionmodel.geometry.resources.EclipseTypes;

import java.util.ArrayList;
import java.util.List;

import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;
import static missionmodel.generated.ActivityActions.spawn;

@ActivityType("AddSpacecraftEclipses")
public class AddSpacecraftEclipses {

  @Export.Parameter
  public gov.nasa.jpl.aerie.merlin.protocol.types.Duration searchDuration;

  @Export.Parameter
  public String observer;
  @Export.Parameter
  public String target;
  @Export.Parameter
  public String occultingBody;
  @Export.Parameter
  public gov.nasa.jpl.aerie.merlin.protocol.types.Duration stepSize;
  @Export.Parameter
  public Boolean useDSK;

  @ActivityType.EffectModel
  public void run(Mission model){
    SpiceDirectEventGenerator generator = new SpiceDirectEventGenerator();

    List<Window> eclipses;

    try {
      // false, false because if a DSK is not used, we want an ellipsoid instead of a point source as eclipsing body,
      // and we don't want to merge partials
      eclipses = generator.getOccultations( JPLTimeConvertUtility.nowJplTime(model.absoluteClock),
        JPLTimeConvertUtility.jplTimeFromUTCInstant(
          model.absoluteClock.now().plusMillis( searchDuration.in(Duration.MILLISECOND) )),
        JPLTimeConvertUtility.getJplTimeDur(stepSize), observer, target, occultingBody,"CN", false, false, useDSK);
    } catch (GeometryInformationNotAvailableException e) {
      eclipses = new ArrayList<>();
    }

    Duration durToSearchEnd = searchDuration;

    // There may be some eclipses that are before the start of this activity. Remove them or alter the start time to start
    // at the start time of the activity
    Time actStart = JPLTimeConvertUtility.nowJplTime(model.absoluteClock);
    while (!eclipses.isEmpty() && eclipses.get(0).getStart().lessThan(actStart)) {
      if (eclipses.get(0).getEnd().lessThanOrEqualTo(actStart)) {
        eclipses.remove(0);
      } else if (eclipses.get(0).getStart().lessThan(actStart)) {
        eclipses.set(0, new Window(actStart, eclipses.get(0).getEnd(), eclipses.get(0).getType()) );
      }
    }

    for(int i = 0; i < eclipses.size(); i++){
      // we assume that this is eclipses of the spacecraft - other body eclipses should be in mission-specific code
      // Don't spawn any eclipses at or after the end of the search duration
      if (eclipses.get(i).getStart().lessThan(actStart.plus(JPLTimeConvertUtility.getJplTimeDur(searchDuration)))) {
        Duration delayTime = JPLTimeConvertUtility.getDuration(
          eclipses.get(i).getStart().minus(JPLTimeConvertUtility.nowJplTime(model.absoluteClock)));
        delay(delayTime);
        spawn(model, new SpacecraftEnterEclipse(occultingBody,
          EclipseTypes.valueOf(eclipses.get(i).getType()),
          JPLTimeConvertUtility.getDuration(eclipses.get(i).getDuration())));
        durToSearchEnd = durToSearchEnd.minus(delayTime);

        // while we always enter an eclipse, we don't always exit - we could transition from full to partial eclipse
        if (i == eclipses.size() - 1 ||
          eclipses.get(i).getEnd().absoluteDifference(eclipses.get(i + 1).getStart()).greaterThan(
            JPLTimeConvertUtility.getJplTimeDur(Duration.SECOND))) {
          delayTime = JPLTimeConvertUtility.getDuration(
            eclipses.get(i).getEnd().minus(JPLTimeConvertUtility.nowJplTime(model.absoluteClock)));

          // Check to make sure the end of the eclipse is not past the end of the search window. If it is, we are done
          // and do not need to add an exit eclipse activity
          if (durToSearchEnd.minus(delayTime).isPositive()) {
            delay(delayTime);
            spawn(model, new SpacecraftExitEclipse(occultingBody));
            durToSearchEnd = durToSearchEnd.minus(delayTime);
          }
        }
      }
    }

    // Make the activity span the entire search window
    delay (durToSearchEnd);
  }
}
