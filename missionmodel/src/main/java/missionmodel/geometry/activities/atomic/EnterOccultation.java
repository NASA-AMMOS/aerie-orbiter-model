package missionmodel.geometry.activities.atomic;

import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export.Parameter;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import missionmodel.Mission;

import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;

@ActivityType("EnterOccultation")
public class EnterOccultation {

  @Parameter
  public String body;
  @Parameter
  public String station;

  public EnterOccultation() {};

  public EnterOccultation(String body, String station) {
    this.body = body;
    this.station = station;
  }

  @ActivityType.EffectModel
  public void run(Mission model){
    //setGroup("OccultationEvents");
    // setName("EnterOccultation_" + body + "_SeenFrom_" + station);
    DiscreteEffects.increment(model.geometryResources.Occultation, 1);
    DiscreteEffects.turnOn(model.geometryResources.SpacecraftOccultationByBodyAndStation.get(body).get(station));
    delay(Duration.SECOND);
  }
}
