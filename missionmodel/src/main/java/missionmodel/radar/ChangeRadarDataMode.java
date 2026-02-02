package missionmodel.radar;

import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import missionmodel.Mission;
import missionmodel.data.activities.ChangeDataGenerationRate;

import static missionmodel.generated.ActivityActions.call;

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
    // Spawn an activity to playback -- put it in a random bin
    double newRate = mode.getDataRate();
    int bin = model.getRandom().nextInt(model.data.unfilteredOnboardBuckets.size());
    call(model, new ChangeDataGenerationRate(bin, newRate*1e6));

    DiscreteEffects.set(model.radarModel.RadarDataMode, mode);
  }
}
