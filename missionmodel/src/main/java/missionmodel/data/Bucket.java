package missionmodel.data;

import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.core.Resource;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.Registrar;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.LinearBoundaryConsistencySolver;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.Polynomial;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

import java.util.List;

import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.*;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.PolynomialEffects.*;
import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;
import static gov.nasa.jpl.aerie.contrib.streamline.core.Reactions.wheneverDynamicsChange;
import static gov.nasa.jpl.aerie.contrib.streamline.core.monads.ResourceMonad.map;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteResources.choose;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.LinearBoundaryConsistencySolver.Comparison.GreaterThanOrEquals;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.LinearBoundaryConsistencySolver.Comparison.LessThanOrEquals;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.LinearBoundaryConsistencySolver.LinearExpression.lx;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.Polynomial.polynomial;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.PolynomialResources.*;
import static missionmodel.data.Data.rateSolver;

/**
 * A container or category representing volume and constant & linear changes in the volume of something (e.g., data).
 * A Bucket has an upper bound on volume.  A Bucket may also have child buckets, whose volumes sum to the parent's
 * volume.  Thus, children can be indirectly limited in volume.  Children are prioritized by their order in a List.
 * This ordering affects rates of change when the parent has reached its limit.  A Bucket cannot have a negative volume.
 */
public class Bucket {
  /**
   * A human-usable name associated with this {@link Bucket}
   */
  String name;
  /**
   * The actual rate of change of volume after clamping the desired rate, {@link #desiredRate}, based on the upper bound, {@link #volume_ub}
   */
  public Resource<Polynomial> actualRate = null;
  /**
   * The volume in this {@link Bucket}
   */
  public Resource<Polynomial> volume;
  /**
   * The desired net rate of change of the volume equal to {@link #desiredReceiveRate} minus {@link #desiredRemoveRate}
   */
  public Resource<Polynomial> desiredRate = null;
  /**
   * As separate resource matching {@code volume}, needed for handling circular dependencies
   */
  private Resource<Polynomial> correctedVolume;

  /**
   * {@link Bucket}s which categorize (or constitute) the volume in this {@link Bucket} in order of priority
   */
  public List<Bucket> children;

  /**
   * An intermediate computation of the volume after clamping based on the upper bound, {@link #volume_ub}, and
   * an implicit lower bound of zero for an empty {@link Bucket}
   */
  Resource<Polynomial> clampedVolume = null;
  /**
   * The upper bound on the volume, either specified by the user or computed from the parent's upper bound and
   * those of higher priority siblings
   */
  public Resource<Polynomial> volume_ub;

  /**
   * The volume actually received, given that some desired amount could not be stored due to {@link #volume_ub}
   */
  public MutableResource<Polynomial> received;
  /**
   * The volume that would have been received if there were no upper bound
   */
  private Resource<Polynomial> desiredReceived;  // The difference with received is lost volume.
  /**
   * The rate of incoming volume desired to be received/stored
   */
  public Resource<Polynomial> desiredReceiveRate;
  /**
   * The volume actually removed, given that some desired amount was not always available to remove
   */
  public MutableResource<Polynomial> removed;
  /**
   * The volume that would have been removed if the volume were always available to remove as desired
   */
  private Resource<Polynomial> desiredRemoved;  // This should be the same as removed; otherwise, it is user error.
  /**
   * The rate of volume desired to be removed/deleted from this {@link Bucket}
   */
  public Resource<Polynomial> desiredRemoveRate;
  /**
   * The rate of volume actually received, given that some desired amount could not be stored due to {@link #volume_ub}
   */
  public Resource<Polynomial> receiveRate;
  /**
   * The rate of volume actually removed/deleted, given that some desired amount may not have been available to delete
   */
  public Resource<Polynomial> removeRate;

  private static Resource<Polynomial> max_bound = constant(Double.MAX_VALUE);

  /**
   * Create a {@link Bucket} without an explicit upper bound on its volume
   * @param name the name of the {@link Bucket}
   * @param isChild whether or not this {@link Bucket} is a child of another
   * @param children the child {@link Bucket}s in priority order
   */
  public Bucket(String name, boolean isChild, List<Bucket> children) {
    this(name, isChild, children, max_bound);
  }

  /**
   * Create a {@link Bucket} without an explicit upper bound on its volume
   * @param name the name of the {@link Bucket}
   * @param isChild whether or not this {@link Bucket} is a child of another
   * @param children the child {@link Bucket}s in priority order
   * @param upperBound an explicit upper bound on this {@link Bucket}, implicitly imposed on any children
   */
  public Bucket(String name, boolean isChild, List<Bucket> children, Resource<Polynomial> upperBound) {
    this.name = name;
    this.desiredReceiveRate = polynomialResource(0.0);
    this.desiredRemoveRate = polynomialResource(0.0);
    this.received = polynomialResource(0.0);
    this.removed = polynomialResource(0.0);
    this.volume = polynomialResource(0.0);
    this.volume_ub = upperBound;

    this.correctedVolume = null;

    this.children = children;

    // The code below uses a built-in arc-consistency solver (LinearBoundaryConsistencySolver) and forward()
    // in order to handle cyclic dependencies among parent and child Buckets.
    LinearBoundaryConsistencySolver.LinearExpression sumExpr = null;
    for (int i = 0; i < this.children.size(); ++i) {  // TODO -- what if a child has children?
      Bucket child = this.children.get(i);
      var rate = rateSolver.variable("rate " + child.name, LinearBoundaryConsistencySolver.Domain::upperBound);
      child.actualRate = rate.resource();
      if (i == 0) {
        child.volume_ub = child.volume_ub.equals(max_bound) ? volume_ub : min(child.volume_ub, volume_ub);
      } else {
        var child_volume_ub = subtract(children.get(i - 1).volume_ub, children.get(i - 1).clampedVolume);
        child.volume_ub = child.volume_ub.equals(max_bound) ? child_volume_ub : min(child.volume_ub, child_volume_ub);
      }
      child.clampedVolume = clamp(child.volume, constant(0), child.volume_ub);
      child.correctedVolume = map(child.clampedVolume, child.actualRate, (v, r) -> r.integral(v.extract()));
      forward(eraseExpiry(child.correctedVolume), (MutableResource<Polynomial>) child.volume);

      if (sumExpr == null) sumExpr = lx(rate);
      else sumExpr = sumExpr.add(lx(rate));

      child.desiredRate = subtract(child.desiredReceiveRate, child.desiredRemoveRate);
      var isEmpty = lessThanOrEquals(child.volume, 0);
      var rate_ub = choose(isEmpty, max(child.desiredRate, constant(0)), child.desiredRate);
      rateSolver.declare(lx(rate), LessThanOrEquals, lx(rate_ub));

      var rate_lb = choose(isEmpty, constant(0), constant(-Double.MAX_VALUE));
      rateSolver.declare(lx(rate), GreaterThanOrEquals, lx(rate_lb));
      child.finishInit();
    }
    if (!this.children.isEmpty()) {
      // When full, we never write more than the upper bound will tolerate, in total
      var totalVolume = sum(children.stream().map(b -> b.volume));
      var isFull = greaterThanOrEquals(totalVolume, volume_ub);
      var totalRate_ub = choose(isFull, differentiate(volume_ub), constant(Double.MAX_VALUE));
      rateSolver.declare(sumExpr, LessThanOrEquals, lx(totalRate_ub));
      this.volume = totalVolume;
      actualRate = sum(children.stream().map(b -> b.actualRate));
      desiredReceiveRate = sum(children.stream().map(b -> b.desiredReceiveRate));
      desiredRemoveRate = sum(children.stream().map(b -> b.desiredRemoveRate));
    }
    if (!isChild) {
      finishInit();
    }
  }

  /**
   * A part of the initialization done at different times for the parent and children since they are codependent
   */
  public void finishInit() {
    if (desiredRate == null) desiredRate = subtract(desiredReceiveRate, desiredRemoveRate);
    desiredReceived = map(desiredReceiveRate, r -> r.integral(0.0));
    desiredRemoved = map(desiredRemoveRate, r -> r.integral(0.0));

    if (actualRate == null) actualRate = desiredRate;
    receiveRate = subtract(desiredReceiveRate, max(subtract(desiredRate, actualRate), constant(0.0)));
    removeRate = subtract(desiredRemoveRate, max(subtract(actualRate, desiredRate), constant(0.0)));
    wheneverDynamicsChange(receiveRate, r -> MutableResource.set(received, polynomial(currentValue(received), data(r).extract())));
    wheneverDynamicsChange(removeRate, r -> MutableResource.set(removed, polynomial(currentValue(removed), data(r).extract())));
  }

  /**
   * Specify what resource data to collect for display
   * @param registrar
   */
  public void registerStates(Registrar registrar) {
    registrar.real(name + ".desiredReceiveRate", assumeLinear(desiredReceiveRate));
    registrar.real(name + ".desiredRemoveRate", assumeLinear(desiredRemoveRate));
    registrar.real(name + ".desiredRate", assumeLinear(desiredRate));
    registrar.real(name + ".actualRate", assumeLinear(actualRate));
    registrar.real(name + ".desiredReceivedVolume", assumeLinear(desiredReceived));
    registrar.real(name + ".desiredRemovedVolume", assumeLinear(desiredRemoved));
    registrar.real(name + ".receivedVolume", assumeLinear(received));
    registrar.real(name + ".removedVolume", assumeLinear(removed));
    registrar.real(name + ".volume", assumeLinear(volume));
    if (clampedVolume != null) registrar.real(name + ".clampedVolume", assumeLinear(clampedVolume));
    if (correctedVolume != null) registrar.real(name + ".correctedVolume", assumeLinear(correctedVolume));
    registrar.real(name + ".maxVolume", assumeLinear(volume_ub));
    for (Bucket child : this.children) {
      child.registerStates(registrar);
    }
  }


  /**
   * Add an incoming rate of volume over a duration to the existing {@link #desiredReceiveRate}.
   * @param rate desired rate of volume to receive
   * @param duration the duration over which the volume is coming in
   */
  public void receive(double rate, Duration duration) {
    if (duration.isEqualTo(Duration.ZERO)) return; // TODO -- warning?
    if (rate == 0) return;
    if (rate > 0) {
      restore((MutableResource<Polynomial>) desiredReceiveRate, rate);
      delay(duration);
      consume((MutableResource<Polynomial>) desiredReceiveRate, rate);
    } else {
      // TODO -- put a warning here?
      remove(-rate, duration);
    }
  }

  /**
   * Add a rate to remove/delete volume over a duration to the existing {@link #desiredRemoveRate}.
   * @param rate
   * @param duration
   */
  public void remove(double rate, Duration duration) {
    if (duration.isEqualTo(Duration.ZERO)) return; // TODO -- warning?
    if (rate == 0) return;
    if (rate > 0) {
      restore((MutableResource<Polynomial>) desiredRemoveRate, rate);
      delay(duration);
      consume((MutableResource<Polynomial>) desiredRemoveRate, rate);
    } else {
      // TODO -- put a warning here?
      receive(-rate, duration);
    }
  }

  /**
   * Add an incoming rate of volume to the existing {@link #desiredReceiveRate}.
   * @param rate
   */
  public void addReceiveRate(double rate) {
    if (rate == 0) return;
    if (rate > 0) {
      restore((MutableResource<Polynomial>) desiredReceiveRate, rate);
    } else {
      // TODO -- put a warning here?
      restore((MutableResource<Polynomial>) desiredRemoveRate, -rate);
    }
  }
  /**
   * Add a rate to remove/delete volume to the existing {@link #desiredRemoveRate}.
   * @param rate
   */
  public void addRemoveRate(double rate) {
    if (rate == 0) return;
    if (rate > 0) {
      restore((MutableResource<Polynomial>) desiredRemoveRate, rate);
    } else {
      // TODO -- put a warning here?
      restore((MutableResource<Polynomial>) desiredReceiveRate, -rate);
    }
  }

  /**
   * Attempt to receive a specified amount immediately (over 1 second).
   * @param amount
   */
  public void receive(double amount) {
    if (amount == 0) return;
    if (amount < 0) {
      remove(-amount);
    } else {
      double actualAmount = Math.min(amount, currentValue(volume_ub) - currentValue(volume));
      var duration = Duration.of(1, Duration.SECOND);
      receive(actualAmount, duration);
    }
  }

  /**
   * Attempt to remove a specified amount immediately (over 1 second).
   * @param amount
   */
  public void remove(double amount) {
    if (amount == 0) return;
    if (amount < 0) {
      receive(-amount);
    } else {
      double actualAmount = Math.min(amount, currentValue(volume));
      var duration = Duration.of(1, Duration.SECOND);
      remove(actualAmount, duration);
    }
  }

}
