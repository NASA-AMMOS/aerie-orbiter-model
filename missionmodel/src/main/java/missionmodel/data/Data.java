package missionmodel.data;

import gov.nasa.jpl.aerie.contrib.serialization.mappers.IntegerValueMapper;
import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.core.Resource;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.Registrar;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.monads.DiscreteResourceMonad;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.Polynomial;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.PolynomialResources;
import org.apache.commons.lang3.tuple.Pair;

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
    // Compute downlink rates imperatively (avoids O(N²) reactive chain)
    // This callback fires when any relevant input changes and recomputes all bin rates
    Runnable computeDownlinkRates = () -> {
      boolean done = currentValue(volumeRequestedToDownlink) <= 0 &&
                     currentValue(durationRequestedToDownlink) <= 0;
      double rateLeft = done ? 0.0 : currentValue(this.dataRate);

      for (int i = 0; i < filteredOnboard.children.size(); ++i) {
        Bucket scBin = filteredOnboard.children.get(i);
        Bucket gBin = ground.children.get(i);

        double availableVolume = currentValue(scBin.received) - currentValue(gBin.received);
        boolean binIsEmpty = currentValue(scBin.volume) <= 0 || availableVolume <= 0 || done;

        double binRate;
        if (binIsEmpty) {
          binRate = Math.max(0, Math.min(currentValue(scBin.actualRate), rateLeft));
        } else {
          binRate = rateLeft;
        }

        set((MutableResource<Polynomial>) gBin.desiredReceiveRate, Polynomial.polynomial(binRate));
        rateLeft -= binRate;
      }
    };

    // Trigger recomputation when data rate or request changes
    wheneverDynamicsChange(this.dataRate, r -> computeDownlinkRates.run());
    wheneverDynamicsChange(volumeRequestedToDownlink, r -> computeDownlinkRates.run());
    wheneverDynamicsChange(durationRequestedToDownlink, r -> computeDownlinkRates.run());

    // Also trigger when any bin's volume, received, or actualRate changes
    for (int i = 0; i < filteredOnboard.children.size(); ++i) {
      Bucket scBin = filteredOnboard.children.get(i);
      Bucket gBin = ground.children.get(i);
      wheneverDynamicsChange(scBin.volume, r -> computeDownlinkRates.run());
      wheneverDynamicsChange(scBin.received, r -> computeDownlinkRates.run());
      wheneverDynamicsChange(scBin.actualRate, r -> computeDownlinkRates.run());
      wheneverDynamicsChange(gBin.received, r -> computeDownlinkRates.run());
    }

    wheneverDynamicsChange(ground.actualRate, r -> {
      if (currentValue(volumeRequestedToDownlink) > 0)
        set(volumeRequestedToDownlink, Polynomial.polynomial(currentValue(volumeRequestedToDownlink), -data(r).extract()));
    });

    // Initial computation
    spawn(computeDownlinkRates);
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
    registerEmptyPrio(registrar);
    registerDownlinkedPrio(registrar);
    registerGroupedBinVolumes(registrar);
  }

  /**
   * Register grouped bin volume resources for visualization.
   * Groups bins into ranges of 5 (0-4, 5-9, etc.) and sums their volumes.
   * This provides a more compact view of bin fullness without X individual line layers.
   */
  private void registerGroupedBinVolumes(Registrar registrar) {
    int groupSize = 5;
    int numBins = filteredOnboardBuckets.size();
    int numGroups = (numBins + groupSize - 1) / groupSize; // ceiling division

    for (int g = 0; g < numGroups; g++) {
      int startBin = g * groupSize;
      int endBin = Math.min(startBin + groupSize, numBins);

      // Collect volume resources for this group
      List<Resource<Polynomial>> groupVolumes = new ArrayList<>();
      for (int i = startBin; i < endBin; i++) {
        groupVolumes.add(filteredOnboardBuckets.get(i).volume);
      }

      // Sum the volumes in this group
      Resource<Polynomial> groupTotal = groupVolumes.stream()
          .reduce(PolynomialResources::add)
          .orElse(polynomialResource(0.0));

      // Register with name like "onboard.binGroup_00_09.volume"
      String groupName = String.format("onboard.binGroup_%02d_%02d.volume", startBin, endBin - 1);
      registrar.real(groupName, assumeLinear(groupTotal));
    }
  }


  private void registerEmptyPrio(Registrar registrar) {
    // Build downlink prio info state
    List<Resource<Discrete<Boolean>>> childrenIsEmptyResources = filteredOnboard.children.stream().map(c -> c.isEmpty).toList();
    List<Resource<Discrete<Pair<Boolean, Integer>>>> indexedChildrenIsEmptyResources = new ArrayList<>();

    // build list from lowest prio bin -> highest
    for (int i = childrenIsEmptyResources.size() - 1; i >= 0; --i) {
      final int fixedI = i;
      final var child = childrenIsEmptyResources.get(i);
      indexedChildrenIsEmptyResources.add(DiscreteResourceMonad.map(child, $ -> Pair.of($, fixedI)));
    }

    // what is the highest prio non-empty bin?
    Resource<Discrete<Pair<Boolean, Integer>>> indexedFirstEmptyChild = DiscreteResourceMonad.reduce(
            indexedChildrenIsEmptyResources,
            Pair.of(false, -1),
            (Pair<Boolean, Integer> first, Pair<Boolean, Integer> second) -> !second.getKey() ? second : first);
    Resource<Discrete<Integer>> indexOfFirstEmptyChild = DiscreteResourceMonad.map(indexedFirstEmptyChild, Pair::getValue);

    registrar.discrete(filteredOnboard.name + ".highestDownlinkPriority", indexOfFirstEmptyChild, new IntegerValueMapper());
  }

  private void registerDownlinkedPrio(Registrar registrar) {
    // Build downlink prio info state
    List<Resource<Discrete<Boolean>>> childrenIsEmptyResources = ground.children
            .stream()
            .map(c -> PolynomialResources.greaterThan(c.actualRate, 0)).toList();
    List<Resource<Discrete<Pair<Boolean, Integer>>>> indexedChildrenIsEmptyResources = new ArrayList<>();

    // build list from lowest prio bin -> highest
    for (int i = childrenIsEmptyResources.size() - 1; i >= 0; --i) {
      final int fixedI = i;
      final var child = childrenIsEmptyResources.get(i);
      indexedChildrenIsEmptyResources.add(DiscreteResourceMonad.map(child, $ -> Pair.of($, fixedI)));
    }

    // what is the highest prio non-empty bin?
    Resource<Discrete<Pair<Boolean, Integer>>> indexedFirstEmptyChild = DiscreteResourceMonad.reduce(
            indexedChildrenIsEmptyResources,
            Pair.of(false, -1),
            (Pair<Boolean, Integer> first, Pair<Boolean, Integer> second) -> second.getKey() ? second : first);
    Resource<Discrete<Integer>> indexOfFirstEmptyChild = DiscreteResourceMonad.map(indexedFirstEmptyChild, Pair::getValue);

    registrar.discrete(ground.name+".currentDownlinkPriority", indexOfFirstEmptyChild, new IntegerValueMapper());
  }
}
