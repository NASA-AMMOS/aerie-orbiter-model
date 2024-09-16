package missionmodel.radar;

import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.core.Resources;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.Polynomial;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import missionmodel.Mission;
import missionmodel.data.Data;
import missionmodel.data.activities.ChangeDataGenerationRate;
import missionmodel.data.activities.PlaybackData;

import static gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource.set;
import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.currentValue;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.Polynomial.polynomial;
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
    // spawn(model, new ChangeDataGenerationRate(0, newRate*1e6)); // Conversion from Mbps -> bps
    // @todo Temporary injection of Data Model Code - remove
    Data data = model.getData();
    var binToChange = data.getOnboardBin(0);

    if (newRate > 0) {
      set((MutableResource<Polynomial>)binToChange.desiredReceiveRate, polynomial(newRate*1e6));
      set((MutableResource<Polynomial>)binToChange.desiredRemoveRate, polynomial(0));
    } else {
      set((MutableResource<Polynomial>) binToChange.desiredReceiveRate, polynomial(0));
      set((MutableResource<Polynomial>) binToChange.desiredRemoveRate, polynomial(-newRate*1e6));
    }
    // @todo End of Temporary code

    DiscreteEffects.set(model.radarModel.RadarDataMode, mode);
  }
}
