package missionmodel.geometry.activities.atomic;

import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import missionmodel.Mission;

import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;

@ActivityType("ExitOccultation")
public class ExitOccultation {

  @Export.Parameter
  public String body;
  @Export.Parameter
  public String station;

  public ExitOccultation() {};

  public ExitOccultation(String body, String station) {
    this.body = body;
    this.station = station;
  }

  @ActivityType.EffectModel
  public void run(Mission model){
    //setGroup("OccultationEvents");
    // setName("EnterOccultation_" + body + "_SeenFrom_" + station);
    DiscreteEffects.decrement(model.geometryResources.Occultation, 1);
    DiscreteEffects.turnOff(model.geometryResources.SpacecraftOccultationByBodyAndStation.get(body).get(station));
  }
}
