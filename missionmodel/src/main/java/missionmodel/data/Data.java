package missionmodel.data;

import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.core.Resource;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.Registrar;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.LinearBoundaryConsistencySolver;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.Polynomial;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.PolynomialResources;
import missionmodel.data.Bucket;

import java.util.*;

import static gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource.set;
import static gov.nasa.jpl.aerie.contrib.streamline.core.Reactions.wheneverDynamicsChange;
import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.*;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteResources.*;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.PolynomialResources.*;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.PolynomialResources.min;
import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.spawn;

/**
 * The Data class is the main interface for using the data model.  A mission model can construct a Data object
 * containing data volume bins and the parent storage with the storage limit.  See the
 * [Model Behavior Description]({@docRoot}/docs/ModelBehaviorDesc.md) for a description of how {@link Bucket}
 * (bin) resources are updated.  That functionality is implemented by this class.  This class also automatically
 * registers resources for the bins.
 */
public class Data {
  public static LinearBoundaryConsistencySolver rateSolver = new LinearBoundaryConsistencySolver("DataModel Rate Solver");

  /**
   * The onboard storage device of the spacecraft, a parent of the bins, {@link #onboardBuckets}.
   */
  public Bucket onboard;

  /**
   * The parent container for ground storage, representing the data that has been played back/downlinked overall
   * and for each bin through its children, {@link #groundBuckets}.
   */
  public Bucket ground;

  /**
   * A playbackdatarate resource provided by the user; if unspecified in the Data constructor,
   * a default value will be used.
   */
  public Resource<Polynomial> dataRate;  // bps

  /**
   * When a {@link gov.nasa.jpl.aerie_data.activities.PlaybackData} activity has a volume goal, this resource tracks
   * how much volume is left before the goal has been met.
   */
  public MutableResource<Polynomial> volumeRequestedToDownlink = polynomialResource(0.0);

  /**
   * When a {@link gov.nasa.jpl.aerie_data.activities.PlaybackData} activity has a duration goal, this resource tracks
   * how much time is left before the goal has been met.
   */
  public MutableResource<Polynomial> durationRequestedToDownlink = polynomialResource(0.0);

  /**
   * The storage bins/categories, which are children of {@link #onboard}.  Lower indices in the array are higher priority
   */
  public ArrayList<Bucket> onboardBuckets = new ArrayList<>();

  /**
   * The ground storage bins corresponding to the onboard bins, tracking how much data has been downlinked for each bin
   */
  public ArrayList<Bucket> groundBuckets = new ArrayList<>();

  /**
   * Get the onboard bin by index, starting from 0
   */
  public Bucket getOnboardBin(int bin) {
    return onboardBuckets.get(bin);
  }

  /**
   * Get the ground bin by index, starting from 0
   */
  public Bucket getGroundBin(int bin) {
    return groundBuckets.get(bin);
  }

  /**
   * Construct a Data object, instantiating a specified number of onboard and corresponding ground bins and
   * using an externally defined data rate and storage limit (max volume) for the total onboard storage.
   * @param dataRate the data rate resource, specified external to the data model, such as by a telecom subsystem model
   * @param numBuckets the number of prioritized bins/categories of data
   * @param maxVolume the onboard storage limit as a resource that is defined set external to the data model
   */
  public Data(Optional<Resource<Polynomial>> dataRate, int numBuckets, Resource<Polynomial> maxVolume) {

    for (int i = 0; i < numBuckets; ++i) {
      Bucket scBin = new Bucket("scBin" + i, true, Collections.emptyList());
      onboardBuckets.add(scBin);
      Bucket gBin = new Bucket("gndBin" + i, true, Collections.emptyList());
      groundBuckets.add(gBin);
    }

    onboard = new Bucket("onboard", false, onboardBuckets, maxVolume); // 10Gb

    ground = new Bucket("ground", false, groundBuckets);

    if (dataRate.isPresent()) {
      this.dataRate = dataRate.get();
    } else {
      this.dataRate = polynomialResource(1.0);
    }
    var done = and(lessThanOrEquals(volumeRequestedToDownlink, 0),
      lessThanOrEquals(durationRequestedToDownlink, 0));
    Resource<Polynomial> downlinkRateLeft = choose(done, constant(0), this.dataRate);
    ArrayList<Resource<Polynomial>> actualDownlinkRates = new ArrayList<>();//(model.getData().onboard.children.size());
    for (int i = 0; i < onboard.children.size(); ++i) {
      Bucket scBin = onboard.children.get(i);
      Bucket gBin = ground.children.get(i);
      var availableVolumeToDownlink = subtract(scBin.received, gBin.received);
      var isEmpty = or(lessThanOrEquals(scBin.volume, 0),
        or(lessThanOrEquals(availableVolumeToDownlink, 0),
          and(lessThanOrEquals(volumeRequestedToDownlink, 0),
            lessThanOrEquals(durationRequestedToDownlink, 0))));
      var actualDownlinkRate =
        choose(isEmpty, max(constant(0), min(scBin.actualRate, downlinkRateLeft)),
          downlinkRateLeft);
      actualDownlinkRates.add(actualDownlinkRate);
      downlinkRateLeft = PolynomialResources.subtract(downlinkRateLeft, actualDownlinkRate);
      forward(eraseExpiry(actualDownlinkRate), (MutableResource<Polynomial>)gBin.desiredReceiveRate);
    }
    wheneverDynamicsChange(ground.actualRate, r -> {
      if (currentValue(volumeRequestedToDownlink) > 0)
        set(volumeRequestedToDownlink, Polynomial.polynomial(currentValue(volumeRequestedToDownlink), -data(r).extract()));
    });
    spawn(() -> {
      for (int i = 0; i < onboard.children.size(); ++i) {
        Bucket scBin = onboard.children.get(i);
        Bucket gBin = ground.children.get(i);
        set((MutableResource<Polynomial>) gBin.desiredReceiveRate, actualDownlinkRates.get(i).getDynamics().getOrThrow().data());
      }
    });
  }

  /**
   * Register bin and other resources with Aerie to record them in the simulation results and see them in the UI.
   * @param registrar the built-in Registrar object used to register resources
   */
  public void registerStates(Registrar registrar) {
    onboard.registerStates(registrar);
    ground.registerStates(registrar);
    registrar.real("volumeRequestedToDownlink", assumeLinear(volumeRequestedToDownlink));
    registrar.real("durationRequestedToDownlink", assumeLinear(durationRequestedToDownlink));
    registrar.real("playbackDataRate", assumeLinear(dataRate));
  }

}
