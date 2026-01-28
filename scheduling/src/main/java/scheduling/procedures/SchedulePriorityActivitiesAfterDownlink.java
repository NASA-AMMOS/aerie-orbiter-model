package scheduling.procedures;

import gov.nasa.ammos.aerie.procedural.scheduling.ActivityAutoDelete;
import gov.nasa.ammos.aerie.procedural.scheduling.Goal;
import gov.nasa.ammos.aerie.procedural.scheduling.annotations.SchedulingProcedure;
import gov.nasa.ammos.aerie.procedural.scheduling.annotations.WithDefaults;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.DeletedAnchorStrategy;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.EditablePlan;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.NewDirective;
import gov.nasa.ammos.aerie.procedural.timeline.collections.profiles.Real;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.AnyDirective;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.Directive;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.DirectiveStart;
import gov.nasa.ammos.aerie.procedural.timeline.plan.Plan;
import gov.nasa.ammos.aerie.procedural.timeline.plan.SimulationResults;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.EnumValueMapper;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import missionmodel.radar.RadarDataCollectionMode;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UC3: Priority-Based Scheduling with Resource Constraints
 *
 * Schedules TakeRadarObservation activities from a prioritized pool into
 * altitude
 * windows (below threshold), respecting battery SOC constraints.
 *
 * Two modes:
 * - RE-SIMULATION (default): Simulates after each placement for accuracy. Uses
 * chronological strategy to avoid invalidating earlier placements.
 * - VIRTUAL BATTERY: Models battery by integrating power balance. Faster (only
 * 2
 * simulations) but may have ~2-3% prediction error. Uses priority-first
 * strategy which
 * may result in more optimal placements over the re-simulation mode.
 *
 * Scheduling only occurs after the first Downlink activity ends.
 */
@SchedulingProcedure
public record SchedulePriorityActivitiesAfterDownlink(
    double minBatterySOC,
    double altitudeThresholdKm,
    double batteryCapacityWh,
    double busVoltage,
    boolean useVirtualBattery) implements Goal {

  @NotNull
  @Override
  public ActivityAutoDelete shouldDeletePastCreations(
      @NotNull final Plan plan,
      @Nullable final SimulationResults simResults) {
    // Delete activities created by previous runs of this goal
    return new ActivityAutoDelete.AtBeginning(DeletedAnchorStrategy.Cascade, false);
  }

  @WithDefaults
  public static final class Defaults {
    public static double minBatterySOC = 40.0; // 40% minimum battery
    public static double altitudeThresholdKm = 260.0; // Schedule only when altitude < this
    // From BatterySimConfig: 94.5 Ah * 28V = 2646 Wh
    public static double batteryCapacityWh = 2646.0;
    public static double busVoltage = 28.0; // Bus voltage in Volts
    public static boolean useVirtualBattery = false; // false = re-simulate, true = virtual battery model
  }

  @Override
  public void run(EditablePlan plan) {
    System.out.println("[PriorityScheduler] Mode: " + (useVirtualBattery ? "VIRTUAL BATTERY" : "RE-SIMULATION"));

    // Step 1: Simulate to get baseline resource profiles
    SimulationResults sim = plan.latestResults();
    if (sim == null) {
      sim = plan.simulate();
    }
    System.out.println("[PriorityScheduler] Initial simulation complete");

    // Get baseline resource profiles
    Real batterySOC = sim.resource("cbebattery.batterySOC", Real.deserializer()).cache();

    // For virtual battery mode, also get power production and demand profiles
    Real powerProduction = null;
    Real powerDemand = null;
    if (useVirtualBattery) {
      powerProduction = sim.resource("array.powerProduction", Real.deserializer()).cache();
      powerDemand = sim.resource("spacecraft.cbeLoad", Real.deserializer()).cache();
      System.out.println("[PriorityScheduler] Loaded power profiles for virtual battery modeling");
    }

    // Step 2: Get altitude profile and find windows where altitude < threshold
    Real altitude = sim.resource("SpacecraftAltitude_MARS", Real.deserializer()).cache();
    var lowAltitudeWindows = altitude.lessThan(altitudeThresholdKm).highlightTrue();

    List<Pair<Duration, Duration>> altitudeWindows = new ArrayList<>();
    for (var window : lowAltitudeWindows) {
      altitudeWindows.add(Pair.of(window.start, window.end));
    }

    if (altitudeWindows.isEmpty()) {
      System.out.println("[PriorityScheduler] No altitude windows below " + altitudeThresholdKm + " km found");
      plan.commit();
      return;
    }
    System.out.println("[PriorityScheduler] Found " + altitudeWindows.size() + " altitude windows below "
        + altitudeThresholdKm + " km");

    // Step 2b: Find the first Downlink activity - observations must be scheduled
    // after it ends
    var downlinkActivities = sim.instances("Downlink").collect();
    Duration earliestAllowedStart = Duration.ZERO;
    if (!downlinkActivities.isEmpty()) {
      var firstDownlink = downlinkActivities.get(0);
      earliestAllowedStart = firstDownlink.getInterval().end.plus(Duration.MICROSECOND);
      System.out.println("[PriorityScheduler] Scheduling observations after Downlink ends at " + earliestAllowedStart);
    } else {
      System.out.println("[PriorityScheduler] No downlinks found");
      plan.commit();
      return;
    }

    // Filter altitude windows to only include times after downlink ends
    final Duration minStartTime = earliestAllowedStart;
    altitudeWindows = altitudeWindows.stream()
        .filter(w -> w.getRight().longerThan(minStartTime))
        .map(w -> w.getLeft().shorterThan(minStartTime) ? Pair.of(minStartTime, w.getRight()) : w)
        .toList();

    System.out.println("[PriorityScheduler] " + altitudeWindows.size() + " altitude windows available after downlink");

    // Step 3: Get unscheduled TakeRadarObservation activities, sorted by priority
    // (ascending = highest first)
    List<Directive<AnyDirective>> unscheduledRequests = new ArrayList<>(plan.directives("TakeRadarObservation")
        .collect()
        .stream()
        .filter(d -> !isScheduled(d.inner))
        .sorted(Comparator.comparingInt((Directive<AnyDirective> d) -> getPriority(d.inner)))
        .toList());

    if (unscheduledRequests.isEmpty()) {
      System.out.println("[PriorityScheduler] No unscheduled TakeRadarObservation activities found");
      plan.commit();
      return;
    }

    System.out.println("[PriorityScheduler] " + unscheduledRequests.size() + " unscheduled requests (by priority):");
    for (var req : unscheduledRequests) {
      var mode = getMode(req.inner);
      var priority = getPriority(req.inner);
      var duration = getDuration(req.inner);
      System.out.println("  - Priority " + priority + ": " + mode + " (" + duration.in(Duration.MINUTES) + " min)");
    }

    // Track which requests have been placed
    Set<Object> placedRequestIds = new HashSet<>();

    // Track scheduled time ranges to avoid overlap
    List<Pair<Duration, Duration>> scheduledRanges = new ArrayList<>();

    // For virtual battery mode, track placed activities to model cumulative effects
    List<PlacedActivity> placedActivities = new ArrayList<>();

    // Statistics
    int scheduled = 0;

    // Step 4: Schedule activities using mode-appropriate strategy
    // - Virtual battery mode: PRIORITY-FIRST (highest priority gets placed first,
    // in any valid window)
    // - Re-simulation mode: CHRONOLOGICAL (process windows in time order to avoid
    // invalidation)

    if (useVirtualBattery) {
      // PRIORITY-FIRST STRATEGY for virtual battery mode
      // Process requests by priority, finding any valid window for each
      System.out.println("\n[PriorityScheduler] Processing by PRIORITY (virtual battery mode)...");

      for (var request : unscheduledRequests) {
        var mode = getMode(request.inner);
        var priority = getPriority(request.inner);
        var observationDuration = getDuration(request.inner);

        // Try each altitude window to find a valid placement
        Duration bestStart = null;
        for (var window : altitudeWindows) {
          Duration windowStart = window.getLeft();
          Duration windowEnd = window.getRight();
          Duration windowLength = windowEnd.minus(windowStart);

          if (observationDuration.longerThan(windowLength)) {
            continue; // Doesn't fit in this window
          }

          Duration candidateStart = findValidStartTimeVirtual(
              windowStart, windowEnd, observationDuration,
              scheduledRanges, batterySOC,
              powerProduction, powerDemand, placedActivities,
              mode, priority);

          if (candidateStart != null) {
            bestStart = candidateStart;
            break; // Found a valid window, use it
          }
        }

        if (bestStart != null) {
          Duration candidateEnd = bestStart.plus(observationDuration);

          System.out.println("[PriorityScheduler] Priority " + priority + " " + mode +
              ": PLACED at " + bestStart + " for " + observationDuration.in(Duration.MINUTES) + " min");

          createRadarObservation(plan, mode, observationDuration, priority, bestStart);

          // Track this placement
          placedRequestIds.add(request.id);
          scheduledRanges.add(Pair.of(bestStart, candidateEnd.plus(Duration.MICROSECOND)));
          placedActivities.add(new PlacedActivity(bestStart, candidateEnd, mode, priority));
          scheduled++;
        } else {
          System.out.println("[PriorityScheduler] Priority " + priority + " " + mode +
              ": NO VALID WINDOW found");
        }
      }
    } else {
      // CHRONOLOGICAL STRATEGY for re-simulation mode
      // Process windows in time order, packing multiple activities into each window
      System.out.println("\n[PriorityScheduler] Processing CHRONOLOGICALLY (re-simulation mode)...");

      for (var window : altitudeWindows) {
        Duration windowStart = window.getLeft();
        Duration windowEnd = window.getRight();

        // Skip if all requests have been placed
        if (placedRequestIds.size() == unscheduledRequests.size()) {
          break;
        }

        // Track the earliest available time in this window (advances as we place activities)
        Duration currentStart = windowStart;

        // Keep trying to pack activities into this window until none fit
        boolean placedSomethingInWindow = true;
        while (placedSomethingInWindow && placedRequestIds.size() < unscheduledRequests.size()) {
          placedSomethingInWindow = false;
          Duration remainingLength = windowEnd.minus(currentStart);

          // Find the highest-priority unplaced activity that fits in remaining window time
          Directive<AnyDirective> bestCandidate = null;
          Duration bestCandidateStart = null;

          for (var request : unscheduledRequests) {
            if (placedRequestIds.contains(request.id)) {
              continue; // Already placed
            }

            var observationDuration = getDuration(request.inner);

            // Check if observation fits in remaining window time
            if (observationDuration.longerThan(remainingLength)) {
              continue; // Doesn't fit
            }

            // Find earliest valid start time in remaining window
            Duration candidateStart = findValidStartTime(
                currentStart, windowEnd, observationDuration,
                scheduledRanges, batterySOC,
                getMode(request.inner));

            if (candidateStart != null) {
              // This activity can be placed - since we iterate by priority, this is the best
              bestCandidate = request;
              bestCandidateStart = candidateStart;
              break;
            }
          }

          // Place the best candidate if found
          if (bestCandidate != null) {
            var mode = getMode(bestCandidate.inner);
            var priority = getPriority(bestCandidate.inner);
            var observationDuration = getDuration(bestCandidate.inner);
            Duration candidateEnd = bestCandidateStart.plus(observationDuration);

            System.out.println("[PriorityScheduler] Priority " + priority + " " + mode +
                ": PLACED at " + bestCandidateStart + " for " + observationDuration.in(Duration.MINUTES) + " min");

            createRadarObservation(plan, mode, observationDuration, priority, bestCandidateStart);

            // Track this placement
            placedRequestIds.add(bestCandidate.id);
            scheduledRanges.add(Pair.of(bestCandidateStart, candidateEnd.plus(Duration.MICROSECOND)));
            scheduled++;

            // Update current start for next activity in this window
            currentStart = candidateEnd.plus(Duration.MICROSECOND);
            placedSomethingInWindow = true;

            // Re-simulate to get accurate resource profiles for next placement
            sim = plan.simulate();
            batterySOC = sim.resource("cbebattery.batterySOC", Real.deserializer()).cache();
          }
        }
      }
    }

    // Report unplaced activities
    int skipped = 0;
    for (var request : unscheduledRequests) {
      if (!placedRequestIds.contains(request.id)) {
        var mode = getMode(request.inner);
        var priority = getPriority(request.inner);
        System.out.println("[PriorityScheduler] Priority " + priority + " " + mode +
            ": NOT PLACED - no suitable window found");
        skipped++;
      }
    }

    // For virtual battery mode, do a final validation simulation
    if (useVirtualBattery && scheduled > 0) {
      System.out.println("\n[PriorityScheduler] Running final validation simulation...");
      sim = plan.simulate();
      Real finalBatterySOC = sim.resource("cbebattery.batterySOC", Real.deserializer()).cache();

      // Check if any placed activities violate battery constraints
      boolean hasViolation = false;
      for (var placed : placedActivities) {
        // Sample at fine intervals during the activity
        Duration checkStep = Duration.minutes(1);
        Duration t = placed.start;
        while (t.noLongerThan(placed.end)) {
          double actualBattery = finalBatterySOC.sample(t);
          if (actualBattery < minBatterySOC) {
            System.out.println("[PriorityScheduler] WARNING: Virtual battery prediction violated! " +
                "P" + placed.priority + " " + placed.mode + " at " + t +
                " has actual battery=" + String.format("%.1f", actualBattery) + "% < " + minBatterySOC + "%");
            hasViolation = true;
            break;
          }
          t = t.plus(checkStep);
        }
      }
      if (!hasViolation) {
        System.out.println("[PriorityScheduler] Final validation: All placements respect battery constraints!");
      }
    }

    // Summary
    System.out.println("\n[PriorityScheduler] === SUMMARY ===");
    System.out.println("[PriorityScheduler] Mode: " + (useVirtualBattery ? "VIRTUAL BATTERY" : "RE-SIMULATION"));
    System.out.println("[PriorityScheduler] Scheduled: " + scheduled + " activities");
    System.out.println("[PriorityScheduler] Skipped (no suitable window): " + skipped);
    System.out.println("[PriorityScheduler] Altitude windows processed: " + altitudeWindows.size() + " (< "
        + altitudeThresholdKm + " km)");
    if (useVirtualBattery) {
      System.out.println("[PriorityScheduler] Strategy: PRIORITY-FIRST (highest priority placed first)");
      System.out.println("[PriorityScheduler] Simulations performed: 2 (1 initial + 1 final validation)");
    } else {
      System.out.println("[PriorityScheduler] Strategy: CHRONOLOGICAL (windows processed in time order)");
      System.out.println("[PriorityScheduler] Simulations performed: " + (scheduled + 1) + " (1 initial + " + scheduled
          + " per placement)");
    }

    plan.commit();
  }

  /**
   * Helper class to track placed activities for virtual battery modeling.
   */
  private record PlacedActivity(Duration start, Duration end, RadarDataCollectionMode mode, int priority) {
  }

  /**
   * Create a TakeRadarObservation activity with the given parameters.
   */
  private void createRadarObservation(EditablePlan plan, RadarDataCollectionMode mode,
      Duration duration, int priority, Duration startTime) {
    var directive = new NewDirective(
        new AnyDirective(Map.of(
            "mode", new EnumValueMapper<>(RadarDataCollectionMode.class).serializeValue(mode),
            "duration", SerializedValue.of(duration.in(Duration.MICROSECONDS)),
            "scheduled", SerializedValue.of(true),
            "schedulingPriority", SerializedValue.of(priority))),
        "Scheduled_P" + priority + "_" + mode,
        "TakeRadarObservation",
        new DirectiveStart.Absolute(startTime));
    plan.create(directive);
  }

  /**
   * Find the earliest valid start time for an activity within a window
   * (re-simulation mode).
   * Returns null if no valid start time exists.
   */
  private Duration findValidStartTime(
      Duration windowStart, Duration windowEnd, Duration observationDuration,
      List<Pair<Duration, Duration>> scheduledRanges,
      Real batterySOC, RadarDataCollectionMode mode) {

    Duration searchStep = Duration.minutes(1);
    Duration candidateStart = windowStart;

    while (candidateStart.plus(observationDuration).noLongerThan(windowEnd)) {
      Duration candidateEnd = candidateStart.plus(observationDuration);

      // Check for overlap with already scheduled observations
      boolean overlaps = false;
      for (var scheduledRange : scheduledRanges) {
        // If our candidate window does overlap with a scheduled range,
        // we will break and move our candidateStart to the right of the 
        // overlapping scheduledRange + one microsecond to avoid competing 
        // resource mutation during simulation between two adjoining activities
        boolean noOverlap = candidateEnd.shorterThan(scheduledRange.getLeft()) ||
            candidateStart.longerThan(scheduledRange.getRight());
        if (!noOverlap) {
          overlaps = true;
          candidateStart = scheduledRange.getRight().plus(Duration.MICROSECOND);
          break;
        }
      }
      if (overlaps) {
        continue;
      }

      // Check battery constraint - sample directly from simulated profile
      // The profile already reflects all currently placed activities
      double batteryAtEnd = batterySOC.sample(candidateEnd);

      // For battery, we need to estimate the ADDITIONAL drain from this activity
      // The current profile doesn't include this activity yet
      double powerWatts = getPowerForMode(mode);
      double durationHours = observationDuration.in(Duration.MINUTES) / 60.0;
      double drainPercent = (powerWatts * durationHours) / batteryCapacityWh * 100.0;

      // Predicted minimum is at the end, accounting for additional drain
      double predictedMinBattery = batteryAtEnd - drainPercent;

      if (predictedMinBattery < minBatterySOC) {
        candidateStart = candidateStart.plus(searchStep);
        continue;
      }

      // TODO - question, what if you have battery curve where in the middle of your candidate window there is a dip near 0 
      // and then ends up at some satisfactory SOC where you think it's fine to schedule so you do, but then in reality,
      // you end up below min SOC in the middle of that window. Do you need to mock that profile over the duration of the candidate
      // window and verify that it does not fall below min SOC?

      // All constraints satisfied
      return candidateStart;
    }

    return null; // No valid start time found
  }

  private boolean isScheduled(AnyDirective directive) {
    var arg = directive.arguments.get("scheduled");
    return arg == null || arg.asBoolean().orElse(true);
  }

  private int getPriority(AnyDirective directive) {
    var arg = directive.arguments.get("schedulingPriority");
    return arg != null ? arg.asInt().orElse(1L).intValue() : 1;
  }

  private RadarDataCollectionMode getMode(AnyDirective directive) {
    var arg = directive.arguments.get("mode");
    if (arg == null)
      return RadarDataCollectionMode.LOW_RES;
    var modeStr = arg.asString().orElse("LOW_RES");
    return RadarDataCollectionMode.valueOf(modeStr);
  }

  private Duration getDuration(AnyDirective directive) {
    var arg = directive.arguments.get("duration");
    if (arg == null)
      return Duration.minutes(30); // Default 30 minutes
    // Duration is stored as microseconds
    return Duration.of(arg.asInt().orElse(30L * 60 * 1_000_000L), Duration.MICROSECONDS);
  }

  /**
   * Get power consumption in Watts for each radar mode.
   * Based on Radar_State enum: ON_LOW=1000W, ON_MED=2000W, ON_HI=4000W
   */
  private double getPowerForMode(RadarDataCollectionMode mode) {
    return switch (mode) {
      case OFF -> 0.0;
      case LOW_RES -> 1000.0; // Radar_State.ON_LOW
      case MED_RES -> 2000.0; // Radar_State.ON_MED
      case HI_RES -> 4000.0; // Radar_State.ON_HI
    };
  }

  /**
   * Find the earliest valid start time using VIRTUAL battery modeling.
   * Instead of re-simulating, this method computes battery effects by integrating
   * the power production/demand profiles and accounting for already-placed
   * activities.
   *
   * IMPORTANT: This also checks that placing the new activity doesn't invalidate
   * any already-placed activities that occur AFTER this one temporally.
   */
  private Duration findValidStartTimeVirtual(
      Duration windowStart, Duration windowEnd, Duration observationDuration,
      List<Pair<Duration, Duration>> scheduledRanges,
      Real batterySOC, Real powerProduction, Real powerDemand,
      List<PlacedActivity> placedActivities,
      RadarDataCollectionMode mode, int priority) {

    Duration searchStep = Duration.minutes(1);
    Duration candidateStart = windowStart;

    while (candidateStart.plus(observationDuration).noLongerThan(windowEnd)) {
      Duration candidateEnd = candidateStart.plus(observationDuration);

      // Check for overlap with already scheduled observations
      boolean overlaps = false;
      for (var scheduledRange : scheduledRanges) {
        boolean noOverlap = candidateEnd.shorterThan(scheduledRange.getLeft()) ||
            candidateStart.longerThan(scheduledRange.getRight());
        if (!noOverlap) {
          overlaps = true;
          candidateStart = scheduledRange.getRight().plus(Duration.MICROSECOND);
          break;
        }
      }
      if (overlaps) {
        continue;
      }

      // Create a hypothetical list of placed activities INCLUDING this candidate
      List<PlacedActivity> hypotheticalPlacements = new ArrayList<>(placedActivities);
      hypotheticalPlacements.add(new PlacedActivity(candidateStart, candidateEnd, mode, priority));

      // Check battery constraint for THIS activity using virtual battery model
      double minBatteryDuringActivity = computeMinVirtualBatteryDuring(
          candidateStart, candidateEnd, batterySOC, powerProduction, powerDemand,
          placedActivities, mode); // Use current placements (not hypothetical) for this activity

      if (minBatteryDuringActivity < minBatterySOC) {
        // Only log first rejection per window to avoid spam
        if (candidateStart.equals(windowStart)) {
          double baselineAtStart = batterySOC.sample(candidateStart);
          double baselineAtEnd = batterySOC.sample(candidateEnd);
          System.out.println("[PriorityScheduler] DEBUG: P" + priority + " " + mode +
              " rejected at window start " + candidateStart +
              " - minBattery=" + String.format("%.1f", minBatteryDuringActivity) + "% < " + minBatterySOC + "%" +
              " (baseline=" + String.format("%.1f", baselineAtStart) + "%->" + String.format("%.1f", baselineAtEnd)
              + "%)");
        }
        candidateStart = candidateStart.plus(searchStep);
        continue;
      }

      // Check that this placement doesn't invalidate any ALREADY-PLACED activities
      // that occur AFTER this candidate temporally
      boolean invalidatesExisting = false;
      for (var existingPlacement : placedActivities) {
        if (existingPlacement.start.noShorterThan(candidateEnd)) {
          // Create a list that excludes the existing activity we're checking
          // (its power will be added via activityPowerWatts in computeMinVirtualBatteryDuring)
          List<PlacedActivity> otherPlacements = new ArrayList<>();
          for (var p : hypotheticalPlacements) {
            if (p != existingPlacement) {
              otherPlacements.add(p);
            }
          }

          double minBatteryForExisting = computeMinVirtualBatteryDuring(
              existingPlacement.start, existingPlacement.end,
              batterySOC, powerProduction, powerDemand,
              otherPlacements,
              existingPlacement.mode);

          if (minBatteryForExisting < minBatterySOC) {
            System.out.println("[PriorityScheduler] DEBUG: Candidate P" + priority + " " + mode +
                " at " + candidateStart + " would invalidate existing P" + existingPlacement.priority +
                " " + existingPlacement.mode + " at " + existingPlacement.start +
                " (minBattery=" + String.format("%.1f", minBatteryForExisting) + "% < " + minBatterySOC + "%)");
            invalidatesExisting = true;
            break;
          }
        }
      }

      if (invalidatesExisting) {
        candidateStart = candidateStart.plus(searchStep);
        continue;
      }

      // All constraints satisfied
      return candidateStart;
    }

    return null; // No valid start time found
  }

  /**
   * Compute the MINIMUM virtual battery SOC during a proposed activity.
   *
   * Uses direct physics integration: start from baseline SOC at earliest activity,
   * then integrate forward using power balance equations with clamping at 0% and 100%.
   *
   * This is simpler and more accurate than deficit tracking because:
   * - It naturally handles charging, discharging, and clamping
   * - No complex "deficit recovery" logic needed
   * - Same physics as the actual battery model
   */
  private double computeMinVirtualBatteryDuring(
      Duration activityStart,
      Duration activityEnd,
      Real batterySOC,
      Real powerProduction,
      Real powerDemand,
      List<PlacedActivity> placedActivities,
      RadarDataCollectionMode mode) {

    double activityPowerWatts = getPowerForMode(mode);

    // Find earliest activity start (placed or proposed)
    Duration earliestStart = activityStart;
    for (var placed : placedActivities) {
      if (placed.start.shorterThan(earliestStart)) {
        earliestStart = placed.start;
      }
    }

    // Start from the baseline SOC at the earliest relevant time
    double virtualSOC = batterySOC.sample(earliestStart);
    double minBattery = Double.MAX_VALUE;

    Duration integrationStep = Duration.minutes(1);
    Duration t = earliestStart;

    while (t.shorterThan(activityEnd)) {
      Duration nextT = t.plus(integrationStep);
      if (nextT.longerThan(activityEnd)) {
        nextT = activityEnd;
      }
      double dtHours = nextT.minus(t).in(Duration.MINUTES) / 60.0;

      // Calculate total radar power at this timestep
      double radarPower = 0.0;
      for (var placed : placedActivities) {
        if (placed.start.noLongerThan(t) && placed.end.longerThan(t)) {
          radarPower += getPowerForMode(placed.mode);
        }
      }
      if (activityStart.noLongerThan(t) && activityEnd.longerThan(t)) {
        radarPower += activityPowerWatts;
      }

      // Physics integration: net power determines SOC change
      double production = powerProduction.sample(t);
      double baselineDemand = powerDemand.sample(t);
      double netPower = production - baselineDemand - radarPower;

      // Convert power to SOC change: dSOC% = power(W) * time(h) / capacity(Wh) * 100
      double dSOCpercent = netPower * dtHours / batteryCapacityWh * 100.0;

      // Apply change with clamping at 0% and 100%
      virtualSOC = Math.max(0.0, Math.min(100.0, virtualSOC + dSOCpercent));

      // Track minimum ONLY during the proposed activity window
      if (nextT.longerThan(activityStart)) {
        if (virtualSOC < minBattery) {
          minBattery = virtualSOC;
        }
      }

      t = nextT;
    }

    return minBattery;
  }
}
