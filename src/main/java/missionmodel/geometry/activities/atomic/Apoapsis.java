package missionmodel.geometry.activities.atomic;

import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export.Parameter;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import missionmodel.Mission;

import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects.set;
import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;

/**
 * The point at which the spacecraft is furthest to the
 #        center of mass of the body.
 */
@ActivityType("Apoapsis")
public class Apoapsis {

  @Parameter
  public String body = "";

  public Apoapsis() {};

  public Apoapsis(String body) {
    this.body = body;
  }

  @ActivityType.EffectModel
  public void run(Mission model) {
    set(model.geometryResources.Apoapsis.get(body), true);
    delay(Duration.SECOND);
    set(model.geometryResources.Apoapsis.get(body), false);
  }
}
