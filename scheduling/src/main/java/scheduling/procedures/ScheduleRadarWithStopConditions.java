package scheduling.procedures;

import gov.nasa.ammos.aerie.procedural.scheduling.ActivityAutoDelete;
import gov.nasa.ammos.aerie.procedural.scheduling.Goal;
import gov.nasa.ammos.aerie.procedural.scheduling.annotations.SchedulingProcedure;
import gov.nasa.ammos.aerie.procedural.scheduling.annotations.WithDefaults;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.DeletedAnchorStrategy;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.EditablePlan;
import gov.nasa.ammos.aerie.procedural.scheduling.plan.NewDirective;
import gov.nasa.ammos.aerie.procedural.timeline.Interval;
import gov.nasa.ammos.aerie.procedural.timeline.collections.profiles.Real;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.AnyDirective;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.DirectiveStart;
import gov.nasa.ammos.aerie.procedural.timeline.plan.Plan;
import gov.nasa.ammos.aerie.procedural.timeline.plan.SimulationResults;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.EnumValueMapper;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import missionmodel.radar.RadarDataCollectionMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import java.util.Random;

/**
 * UC4: Operator Interaction with Stop Conditions
 *
 * This scheduling procedure schedules radar observations during low-altitude
 * windows (when altitude is below a threshold), using a simulate-then-verify
 * approach with rollback capability.
 *
 * Stop conditions:
 * - No Downlink activity intersecting the altitude window (pre-check)
 * - Battery SOC >= minBatterySOC (pre-check at start, post-check at end)
 * - Data volume <= maxDataVolumeBits (pre-check at start, post-check at end)
 *
 * For each window:
 * 1. Pre-check constraints - skip if already violated
 * 2. Schedule activities for the window
 * 3. Simulate to see actual impact
 * 4. Post-check constraints - rollback if violated, commit if satisfied
 */
@SchedulingProcedure
public record ScheduleRadarWithStopConditions(
    double altitudeThresholdKm,
    double minBatterySOC,
    double maxDataVolumeBits) implements Goal {

  @NotNull
  @Override
  public ActivityAutoDelete shouldDeletePastCreations(
      @NotNull final Plan plan,
      @Nullable final SimulationResults simResults) {
    // Delete activities created by previous runs of this goal, using Cascade to
    // handle anchored activities
    return new ActivityAutoDelete.AtBeginning(DeletedAnchorStrategy.Cascade, false);
  }

  @WithDefaults
  public static final class Defaults {
    public static double altitudeThresholdKm = 260.0; // Schedule when altitude < this
    public static double minBatterySOC = 40.0; // 40% minimum battery
    public static double maxDataVolumeBits = 10_000_000_000.0; // 10 Gb max data
  }

  @Override
  public void run(EditablePlan plan) {
    // Get initial simulation results for resource checking
    SimulationResults sim = plan.latestResults();
    if (sim == null) {
      sim = plan.simulate();
    }

    // Find low-altitude windows using altitude threshold
    Real altitude = sim.resource("SpacecraftAltitude_MARS", Real.deserializer()).cache();
    var lowAltitudeWindows = altitude.lessThan(altitudeThresholdKm).highlightTrue().collect();

    if (lowAltitudeWindows.isEmpty()) {
      System.out.println("[StopConditions] No altitude windows below " + altitudeThresholdKm + " km found");
      plan.commit();
      return;
    }
    System.out.println("[StopConditions] Found " + lowAltitudeWindows.size() + " altitude windows below "
        + altitudeThresholdKm + " km");

    // Get Downlink instances to check for intersections
    var downlinkInstances = sim.instances("Downlink");
    System.out.println("[StopConditions] Found " + downlinkInstances.collect().size() + " Downlink activities");

    // Tracking statistics
    int scheduled = 0;
    int skippedBattery = 0;
    int skippedData = 0;
    int skippedDownlink = 0;

    for (int i = 0; i < lowAltitudeWindows.size(); i++) {
      Interval window = lowAltitudeWindows.get(i);

      // PRE-CHECK: Downlink intersection
      boolean downlinkIntersects = !downlinkInstances.select(window).collect().isEmpty();
      if (downlinkIntersects) {
        System.out.println("[StopConditions] Window " + i + ": SKIPPED - Downlink intersects");
        skippedDownlink++;
        continue;
      }

      // PRE-CHECK: Stop conditions at the start of this window
      StopConditionResult preCheck = checkStopConditions(sim, window.start);

      if (!preCheck.batteryOK) {
        System.out.println("[StopConditions] Window " + i + ": SKIPPED - Battery SOC (" +
            String.format("%.1f", preCheck.batteryValue) + "%) already below minimum (" + minBatterySOC + "%)");
        skippedBattery++;
        continue;
      }

      if (!preCheck.dataOK) {
        System.out.println("[StopConditions] Window " + i + ": SKIPPED - Data volume (" +
            String.format("%.2f", preCheck.dataValue / 1e9) + " Gb) already exceeds maximum (" +
            String.format("%.2f", maxDataVolumeBits / 1e9) + " Gb)");
        skippedData++;
        continue;
      }

      // Pre-checks passed - schedule radar observations for this window
      System.out.println("[StopConditions] Window " + i + ": Pre-check OK - Battery=" +
          String.format("%.1f", preCheck.batteryValue) + "%, Data=" +
          String.format("%.2f", preCheck.dataValue / 1e9) + " Gb. Scheduling activities...");

      // Schedule radar observations using TakeRadarObservation
      // This is the GENERATIVE approach - we compute timing and duration based on window geometry
      Duration windowDur = window.end.minus(window.start);
      long WINDOW_SEGMENTS = 100;
      Duration segDur = windowDur.dividedBy(WINDOW_SEGMENTS);
      Duration nextActTime = window.start;

      // First 80%: LOW_RES (DEM) observation
      Duration lowResDur = segDur.times(80);
      scheduleRadarObservation(plan, nextActTime, RadarDataCollectionMode.LOW_RES, lowResDur, i);
      nextActTime = nextActTime.plus(lowResDur).plus(Duration.MICROSECOND);

      // Next 15%: MED_RES observation
      Duration medResDur = segDur.times(15);
      scheduleRadarObservation(plan, nextActTime, RadarDataCollectionMode.MED_RES, medResDur, i);
      nextActTime = nextActTime.plus(medResDur).plus(Duration.MICROSECOND);

      // Final 5%: HI_RES observation
      Duration hiResDur = segDur.times(5);
      scheduleRadarObservation(plan, nextActTime, RadarDataCollectionMode.HI_RES, hiResDur, i);

      // POST-CHECK: Simulate and verify constraints are still satisfied
      sim = plan.simulate();
      StopConditionResult postCheck = checkStopConditions(sim, window.end);

      if (!postCheck.batteryOK) {
        System.out.println("[StopConditions] Window " + i + ": ROLLBACK - Battery SOC (" +
            String.format("%.1f", postCheck.batteryValue) + "%) dropped below minimum (" + minBatterySOC + "%)");
        plan.rollback();
        sim = plan.simulate();
        skippedBattery++;
        continue;
      }

      if (!postCheck.dataOK) {
        System.out.println("[StopConditions] Window " + i + ": ROLLBACK - Data volume (" +
            String.format("%.2f", postCheck.dataValue / 1e9) + " Gb) exceeded maximum (" +
            String.format("%.2f", maxDataVolumeBits / 1e9) + " Gb)");
        plan.rollback();
        sim = plan.simulate();
        skippedData++;
        continue;
      }

      // Both pre and post checks passed - commit this window's activities
      System.out.println("[StopConditions] Window " + i + ": COMMITTED - Battery=" +
          String.format("%.1f", postCheck.batteryValue) + "%, Data=" +
          String.format("%.2f", postCheck.dataValue / 1e9) + " Gb");
      plan.commit();
      scheduled += 3;
    }

    // Summary
    System.out.println("[StopConditions] === SUMMARY ===");
    System.out.println("[StopConditions] Scheduled: " + scheduled + " radar observations");
    System.out.println("[StopConditions] Skipped (downlink): " + skippedDownlink + " windows");
    System.out.println("[StopConditions] Skipped (low battery): " + skippedBattery + " windows");
    System.out.println("[StopConditions] Skipped (high data volume): " + skippedData + " windows");
  }

  /**
   * Check if stop conditions are satisfied at the given time.
   */
  private StopConditionResult checkStopConditions(SimulationResults sim, Duration checkTime) {
    // Get battery SOC at this time
    var batteryProfile = sim.resource("cbebattery.batterySOC", Real.deserializer());
    double batteryValue = batteryProfile.sample(checkTime);

    // Get data volume at this time
    var dataProfile = sim.resource("onboard.volume", Real.deserializer());
    double dataValue = dataProfile.sample(checkTime);

    boolean batteryOK = batteryValue >= minBatterySOC;
    boolean dataOK = dataValue <= maxDataVolumeBits;

    return new StopConditionResult(batteryOK, batteryValue, dataOK, dataValue);
  }

  /**
   * Helper to schedule a TakeRadarObservation activity with computed duration.
   */
  private void scheduleRadarObservation(EditablePlan plan, Duration startTime, RadarDataCollectionMode mode,
      Duration duration, int windowIndex) {
        var minBin = 0;
        var maxBin = 1;
    Map<String, SerializedValue> actArgs = Map.of(
        "mode", new EnumValueMapper<>(RadarDataCollectionMode.class).serializeValue(mode),
        "duration", SerializedValue.of(duration.in(Duration.MICROSECONDS)),
        "bin", SerializedValue.of(new Random().nextInt(maxBin - minBin + 1) + minBin),
        "scheduled", SerializedValue.of(true),
        "schedulingPriority", SerializedValue.of(windowIndex));

    var directive = new NewDirective(
        new AnyDirective(actArgs),
        "Window" + windowIndex + "_" + mode,
        "TakeRadarObservation",
        new DirectiveStart.Absolute(startTime));
    plan.create(directive);

    System.out.println("[StopConditions] Scheduled " + mode + " observation for window " + windowIndex +
        " at " + startTime + " (duration: " + duration.in(Duration.MINUTES) + " min)");
  }

  /**
   * Result of checking stop conditions.
   */
  private record StopConditionResult(
      boolean batteryOK,
      double batteryValue,
      boolean dataOK,
      double dataValue) {
  }
}
