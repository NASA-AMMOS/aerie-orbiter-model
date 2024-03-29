package missionmodel.geometry.activities.spawner;

import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import missionmodel.JPLTimeConvertUtility;
import missionmodel.Mission;
import missionmodel.geometry.activities.atomic.Periapsis;
import missionmodel.geometry.directspicecalls.SpiceDirectEventGenerator;
import missionmodel.geometry.interfaces.GeometryInformationNotAvailableException;
import gov.nasa.jpl.time.Time;

import java.util.ArrayList;
import java.util.List;

import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;
import static missionmodel.generated.ActivityActions.spawn;

@ActivityType("AddPeriapsis")
public class AddPeriapsis {

  @Export.Parameter
  public gov.nasa.jpl.aerie.merlin.protocol.types.Duration searchDuration;

  @Export.Parameter
  public String body;
  @Export.Parameter
  public String target;
  @Export.Parameter
  public gov.nasa.jpl.aerie.merlin.protocol.types.Duration stepSize;
  @Export.Parameter
  public Double maxDistanceFilter;

  @ActivityType.EffectModel
  public void run(Mission model){
    SpiceDirectEventGenerator generator = new SpiceDirectEventGenerator();

    List<Time> periapsisTimes;

    try {
      periapsisTimes = generator.getPeriapses( JPLTimeConvertUtility.nowJplTime(model.absoluteClock),
        JPLTimeConvertUtility.jplTimeFromUTCInstant(
          model.absoluteClock.now().plusMillis( searchDuration.in(Duration.MILLISECOND) )),
        JPLTimeConvertUtility.getJplTimeDur(stepSize), body, target, maxDistanceFilter, "NONE");
    } catch (GeometryInformationNotAvailableException e) {
      periapsisTimes = new ArrayList<>();
    }

    Duration durToSearchEnd = searchDuration;
    for(Time periapsisTime : periapsisTimes){

      // Effectively wait until each time and spawn an activity
      Duration delayTime = JPLTimeConvertUtility.getDuration(
        periapsisTime.minus( JPLTimeConvertUtility.nowJplTime(model.absoluteClock)));
      delay( delayTime );

      spawn(model, new Periapsis(target));

      durToSearchEnd = durToSearchEnd.minus(delayTime);
    }

    // Make the activity span the entire search window
    delay (durToSearchEnd);
  }
}
