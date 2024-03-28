package missionmodel.geometry.activities.spawner;

import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.time.Time;
import missionmodel.JPLTimeConvertUtility;
import missionmodel.Mission;
import missionmodel.geometry.activities.atomic.Apoapsis;
import missionmodel.geometry.directspicecalls.SpiceDirectEventGenerator;
import missionmodel.geometry.interfaces.GeometryInformationNotAvailableException;

import java.util.ArrayList;
import java.util.List;

import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;
import static missionmodel.generated.ActivityActions.spawn;

@ActivityType("AddApoapsis")
public class AddApoapsis {

  @Export.Parameter
  public gov.nasa.jpl.aerie.merlin.protocol.types.Duration searchDuration;

  @Export.Parameter
  public String body;
  @Export.Parameter
  public String target;
  @Export.Parameter
  public gov.nasa.jpl.aerie.merlin.protocol.types.Duration stepSize;
  @Export.Parameter
  public Double minDistanceFilter;

  @ActivityType.EffectModel
  public void run(Mission model){
    SpiceDirectEventGenerator generator = new SpiceDirectEventGenerator();

    List<Time> apoapsisTimes;

    try {
      apoapsisTimes = generator.getApoapses( JPLTimeConvertUtility.nowJplTime(model.absoluteClock),
        JPLTimeConvertUtility.jplTimeFromUTCInstant(
          model.absoluteClock.now().plusMillis( searchDuration.in(Duration.MILLISECOND) )),
        JPLTimeConvertUtility.getJplTimeDur(stepSize), body, target, minDistanceFilter, "NONE");
    } catch (GeometryInformationNotAvailableException e) {
      apoapsisTimes = new ArrayList<>();
    }

    Duration durToSearchEnd = searchDuration;
    for(Time apoapsisTime : apoapsisTimes){

      // Effectively wait until each time and spawn an activity
      Duration delayTime = JPLTimeConvertUtility.getDuration(
        apoapsisTime.minus( JPLTimeConvertUtility.nowJplTime(model.absoluteClock)));
      delay( delayTime );

      spawn(model, new Apoapsis(target));

      durToSearchEnd = durToSearchEnd.minus(delayTime);
    }

    // Make the activity span the entire search window
    delay (durToSearchEnd);
  }
}
