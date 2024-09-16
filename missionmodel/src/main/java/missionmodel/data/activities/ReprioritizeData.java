package missionmodel.data.activities;

import gov.nasa.jpl.aerie.contrib.streamline.core.Resources;
import gov.nasa.jpl.aerie.merlin.framework.ModelActions;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import missionmodel.data.Data;
import missionmodel.data.DataMissionModel;


import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.currentValue;

@ActivityType("ReprioritizeData")
public class ReprioritizeData {
  /**
   * The volume to reprioritize
   */
  @Export.Parameter
  public double volume; // bits

  /**
   * The bin whose data is to be reprioritized; i.e. the old priority
   */
  @Export.Parameter
  public int bin = 0;

  /**
   * The bin receiving the reprioritized data; i.e., the new priority
   */
  @Export.Parameter
  public int newBin = 1;

  @ActivityType.EffectModel
  public void run(DataMissionModel model) {
    Data data = model.getData();
    var fromBin = data.getOnboardBin(bin);
    var toBin = data.getOnboardBin(newBin);

    double currentVolume = currentValue(fromBin.volume);
    double receivableVolume = currentValue(toBin.volume_ub) - currentValue(toBin.volume);
    double actualVolumeReprioritized = Math.max(0.0, Math.min(volume, Math.min(currentVolume, receivableVolume)));

    System.out.println("ReprioritizeData(" + Resources.currentTime() + "): actualVolumeReprioritized = " + actualVolumeReprioritized);

    ModelActions.spawn(() -> fromBin.remove(actualVolumeReprioritized));
    ModelActions.spawn(() -> toBin.receive(actualVolumeReprioritized));
  }
}
