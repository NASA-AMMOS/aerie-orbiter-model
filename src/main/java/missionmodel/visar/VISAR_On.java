package missionmodel.visar;

import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import missionmodel.Mission;
import missionmodel.power.pel.VISAR_State;

import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;

@ActivityType("VISAR_On")
public class VISAR_On {

  @ActivityType.EffectModel
  public void run(Mission model) {
    DiscreteEffects.set(model.pel.visarState, VISAR_State.ON);
    delay(Duration.SECOND);
  }
}
