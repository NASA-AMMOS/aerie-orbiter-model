package missionmodel.data.activities;

import gov.nasa.jpl.aerie.contrib.streamline.core.Resources;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import missionmodel.data.DataMissionModel;

import java.util.Optional;

@ActivityType("GenerateData")
public class GenerateData {
  /**
   * The bin to generate data in
   */
  @Export.Parameter
  public int bin = 0;

  /**
   * The rate of data generation
   */
  @Export.Parameter
  public Optional<Double> rate = Optional.of(0.0);

  /**
   * The volume of data generation
   */
  @Export.Parameter
  public Optional<Double> volume = Optional.of(0.0);

  /**
   * The duration of data generation
   */
  @Export.Parameter
  public Optional<Duration> duration = Optional.of(Duration.of(1, Duration.SECOND));

  /**
   * At least two of the above parameters above need to be specified
   */
  @Export.Validation("Two or three downlink goals must be specified: rate, volume, and/or duration.")
  @Export.Validation.Subject({"rate", "volume", "duration"})
  public boolean validateNonEmptyGoal() {
    return (rate.isPresent() ? 1 : 0) +
      (volume.isPresent() ? 1 : 0) +
      (duration.isPresent() ? 1 : 0)
      >= 2;
  }

  @ActivityType.EffectModel
  public void run(DataMissionModel model) {
    derivedValues();
    var binToChange = model.getData().getOnboardBin(bin);
    System.out.println("GenerateData(" + Resources.currentTime() + "): rate = " + rate.get() + ", duration = " + duration.get());
    binToChange.receive(rate.get(), duration.get());
  }

  /**
   * Computes the missing value if one of rate, volume, or duration is not specified. If all are specified,
   * checks to ensure that the values agree, otherwise, computes and replaces one of the parameters
   */
  void derivedValues() {
    if (rate.isPresent() && volume.isPresent() && duration.isEmpty()) {
      Double seconds = volume.get() / rate.get();
      duration = Optional.of(Duration.of(seconds.longValue(), Duration.SECONDS));
    } else if (rate.isPresent() && volume.isEmpty() && duration.isPresent()) {
      Double bits = rate.get() * duration.get().in(Duration.SECONDS);
      volume = Optional.of(bits);
    } else if (rate.isEmpty() && volume.isPresent() && duration.isPresent()) {
      Double bps = volume.get() / duration.get().in(Duration.SECONDS);
      rate = Optional.of(bps);
    } else if (rate.isPresent() && volume.isPresent() && duration.isPresent()) {
      Double seconds = volume.get() / rate.get();
      Double bits = rate.get() * duration.get().in(Duration.SECONDS);
      Double bps = volume.get() / duration.get().in(Duration.SECONDS);
      if (seconds < duration.get().in(Duration.SECONDS)) {
        duration = Optional.of(Duration.of(seconds.longValue(), Duration.SECONDS));
      } else if (bits < volume.get()) {
        volume = Optional.of(bits);
      } else if (bps < rate.get()) {
        rate = Optional.of(bps);
      }
    } else {
      throw new RuntimeException("Two or three downlink goals must be specified: rate, volume, and/or duration.");
    }
  }
}
