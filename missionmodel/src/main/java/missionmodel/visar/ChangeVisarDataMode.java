package missionmodel.visar;

import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import missionmodel.Mission;

import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.currentValue;

@ActivityType("ChangeVisarDataMode")
public class ChangeVisarDataMode {

  @Export.Parameter
  public VisarDataCollectionMode mode = VisarDataCollectionMode.DEM;

  @ActivityType.EffectModel
  public void run(Mission model) {
    double currentRate = currentValue(model.visarModel.VisarDataRate);
    double newRate = mode.getDataRate();
    DiscreteEffects.set(model.visarModel.VisarDataMode, mode);
  }
}
