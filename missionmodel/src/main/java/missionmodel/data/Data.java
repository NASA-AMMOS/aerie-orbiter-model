package missionmodel.data;

import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.core.Resource;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.Registrar;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.LinearBoundaryConsistencySolver;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.Polynomial;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.PolynomialResources;

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
   * The unfiltered onboard storage device of the spacecraft.
   */
  public Bucket unfilteredOnboard;


  /**
   * The filtered onboard storage device of the spacecraft, a parent of the bins, {@link #filteredOnboardBuckets}.
   */
  public Bucket filteredOnboard;

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
   * The unfiltered storage bins/categories, which are children of {@link #filteredOnboard}.  Lower indices in the array are higher priority
   */
  public ArrayList<Bucket> unfilteredOnboardBuckets = new ArrayList<>();

  /**
   * The filtered storage bins/categories, which are children of {@link #filteredOnboard}.  Lower indices in the array are higher priority
   */
  public ArrayList<Bucket> filteredOnboardBuckets = new ArrayList<>();

  /**
   * The ground storage bins corresponding to the onboard bins, tracking how much data has been downlinked for each bin
   */
  public ArrayList<Bucket> groundBuckets = new ArrayList<>();

  /**
   * Get the unfiltered bin by index, starting from 0
   */
  public Bucket getUnfilteredBin(int bin) {
    return unfilteredOnboardBuckets.get(bin);
  }

  /**
   * Get the filtered bin by index, starting from 0
   */
  public Bucket getFilteredBin(int bin) {
    return filteredOnboardBuckets.get(bin);
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
      Bucket unfilteredBin = new Bucket("rawBin" + i, true, Collections.emptyList());
      unfilteredOnboardBuckets.add(unfilteredBin);
      Bucket scBin = new Bucket("scBin" + i, true, Collections.emptyList());
      filteredOnboardBuckets.add(scBin);
      Bucket gBin = new Bucket("gndBin" + i, true, Collections.emptyList());
      groundBuckets.add(gBin);
    }

    unfilteredOnboard = new Bucket("unfiltered", false, unfilteredOnboardBuckets, maxVolume);

    filteredOnboard = new Bucket("onboard", false, filteredOnboardBuckets, maxVolume); // 10Gb

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

    for (int i = 0; i < filteredOnboard.children.size(); ++i) {
      Bucket scBin = filteredOnboard.children.get(i);
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
      for (int i = 0; i < ground.children.size(); ++i) {
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
    filteredOnboard.registerStates(registrar);
    unfilteredOnboard.registerStates(registrar);
    ground.registerStates(registrar);
    registrar.real("volumeRequestedToDownlink", assumeLinear(volumeRequestedToDownlink));
    registrar.real("durationRequestedToDownlink", assumeLinear(durationRequestedToDownlink));
    registrar.real("playbackDataRate", assumeLinear(dataRate));
  }

}
