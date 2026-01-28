package missionmodel.radar;

import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import missionmodel.Mission;
import missionmodel.data.activities.ChangeDataGenerationRate;

import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.currentValue;
import static missionmodel.generated.ActivityActions.spawn;

@ActivityType("ChangeRadarDataMode")
public class ChangeRadarDataMode {

  @Export.Parameter
  public RadarDataCollectionMode mode = RadarDataCollectionMode.LOW_RES;

  public ChangeRadarDataMode() {}

  public ChangeRadarDataMode(RadarDataCollectionMode radarDataCollectionMode) {
    this.mode = radarDataCollectionMode;
  }

  @ActivityType.EffectModel
  public void run(Mission model) {
    double currentRate = currentValue(model.radarModel.RadarDataRate);
    double newRate = mode.getDataRate();
    // Spawn an activity to playback
    spawn(model, new ChangeDataGenerationRate(0, newRate*1e6));

    DiscreteEffects.set(model.radarModel.RadarDataMode, mode);
  }
}
