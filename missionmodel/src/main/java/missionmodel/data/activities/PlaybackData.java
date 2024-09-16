package missionmodel.data.activities;

import gov.nasa.jpl.aerie.contrib.streamline.core.Resource;
import gov.nasa.jpl.aerie.contrib.streamline.core.Resources;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.Polynomial;
import gov.nasa.jpl.aerie.merlin.framework.Condition;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.RealDynamics;
import missionmodel.data.DataMissionModel;

import java.util.Optional;

import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.*;
import static gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource.set;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.PolynomialEffects.restore;
import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.waitUntil;

@ActivityType("PlaybackData")
public class PlaybackData {
  /**
   * Desired volume of data to downlink.  May not be achieved if data is not present.
   */
  @Export.Parameter
  public Optional<Double> volume = Optional.empty(); // bits
  @Export.Parameter
  public Optional<Duration> duration = Optional.empty();

  public PlaybackData() {};

  public PlaybackData(Duration duration) {
    this.duration = Optional.of(duration);
  };


  @ActivityType.EffectModel
  public void run(DataMissionModel model) {
    var ground = model.getData().ground;

    if (volume.isPresent() && volume.get() == 0.0) return;
    if (duration.isPresent() && duration.get().isEqualTo(Duration.ZERO)) return;

    if (currentValue(ground.receiveRate) > 0) {
      throw new RuntimeException("Only one PlaybackData activity at a time!");
    }

    final var targetGroundReceivedValue = volume.isEmpty() ? Double.MAX_VALUE : currentValue(ground.received) + volume.get();
    System.out.println("PlaybackData(" + Resources.currentTime() + "): targetGroundReceivedValue = " + targetGroundReceivedValue);
    if (volume.isPresent()) {
      restore(model.getData().volumeRequestedToDownlink, volume.get());
    }
    if (duration.isPresent()) {
      set(model.getData().durationRequestedToDownlink, Polynomial.polynomial(duration.get().in(Duration.SECONDS), -1));
    }
    //waitUntil(when(greaterThanOrEquals(model.getData().ground.received, targetGroundReceivedValue)));  // This creates a cell and dies!
    waitUntil(Condition.and(
      volume.isEmpty() ? Condition.TRUE : isBetween(ground.received, targetGroundReceivedValue, targetGroundReceivedValue * 2), //Double.MAX_VALUE),
      duration.isEmpty() ? Condition.TRUE : isBetween(model.getData().durationRequestedToDownlink, -2.0, 0)));  // -Double.MAX_VALUE
    if (volume.isPresent()) {
      set(model.getData().volumeRequestedToDownlink, Polynomial.polynomial(0, 0));
    }
    if (duration.isPresent()) {
      set(model.getData().durationRequestedToDownlink, Polynomial.polynomial(0, 0));
    }
  }

  private Condition isBetween(Resource<Polynomial> r, final double lower, final double upper) {
    return (positive, atEarliest, atLatest) -> {
      final var p = r.getDynamics().getOrThrow().data();

      if (p.coefficients().length > 2) throw new RuntimeException("isBetween condition only for linear polynomials: resource = " + r);
      RealDynamics dynamics = RealDynamics.linear(p.getCoefficient(0), p.getCoefficient(1));

      return (positive)
        ? dynamics.whenBetween(lower, upper, atEarliest, atLatest)
        : dynamics.whenNotBetween(lower, upper, atEarliest, atLatest);
    };
  }

}
