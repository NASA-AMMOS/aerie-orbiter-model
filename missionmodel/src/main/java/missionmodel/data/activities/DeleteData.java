package missionmodel.data.activities;

import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import missionmodel.data.Data;
import missionmodel.data.DataMissionModel;


import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.currentTime;
import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.currentValue;

@ActivityType("DeleteData")
public class DeleteData {
  /**
   * The maximum volume to delete depending on {@link #limitToSentData} and the volume of the bin
   */
  @Export.Parameter
  public double volume; // bits

  /**
   * Whether to limit the amount deleted to that which has been downlinked
   */
  @Export.Parameter
  public boolean limitToSentData = true;

  /**
   * The bin whose data is to be deleted
   */
  @Export.Parameter
  public int bin = 0;

  public DeleteData() {};

  public DeleteData(double volume, boolean limitToSentData, int bin) {
    this.volume = volume;
    this.limitToSentData = limitToSentData;
    this.bin = bin;
  }

  @ActivityType.EffectModel
  public void run(DataMissionModel model) {
    Data data = model.getData();
    var binToChange = data.getOnboardBin(bin);
    var groundBin = data.getGroundBin(bin);

    double currentVolume = currentValue(binToChange.volume);
    double MAX = Double.MAX_VALUE;
    double volumeNotYetDownlinked = groundBin == null ? MAX : (currentValue(binToChange.received) - currentValue(groundBin.received));
    double volumeAlreadyDownlinked = currentValue(binToChange.volume) - volumeNotYetDownlinked;
    double actualVolumeDeleted =
      Math.min(volume, Math.min(currentVolume, limitToSentData ? volumeAlreadyDownlinked : MAX));
    System.out.println("DeleteData(" + currentTime() + "): actualVolumeDeleted = " + actualVolumeDeleted);

    binToChange.remove(actualVolumeDeleted);

  }


}
