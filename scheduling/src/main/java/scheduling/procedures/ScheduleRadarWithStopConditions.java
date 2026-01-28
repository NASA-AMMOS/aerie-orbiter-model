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
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import static scheduling.SchedulingConstants.*;
import static scheduling.SchedulingUtils.*;

/**
 * UC4: Operator Interaction with Stop Conditions
 *
 * This scheduling procedure schedules radar observations like
 * ScheduleRadarObservations,
 * but checks resource constraints (battery SOC, data volume) before each
 * activity.
 *
 * Stop conditions:
 * - Battery SOC must be >= minBatterySOC before scheduling radar
 * - Onboard data volume must be <= maxDataVolumeBits before scheduling radar
 *
 * When conditions aren't met, activities are skipped and the reason is logged.
 */
@SchedulingProcedure
public record ScheduleRadarWithStopConditions(
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
    public static double minBatterySOC = 20.0; // 20% minimum battery
    public static double maxDataVolumeBits = 20_000_000_000.0; // 20 Gb max data
  }

  @Override
  public void run(EditablePlan plan) {
    Interval planBounds = plan.totalBounds();
    Instant planStart = plan.toAbsolute(planBounds.start);
    Instant planEnd = plan.toAbsolute(planBounds.end);

    // Get initial simulation results for resource checking
    SimulationResults sim = plan.latestResults();
    if (sim == null) {
      sim = plan.simulate();
    }

    // Find Periapsis Activities to define orbit windows
    var periapsisActs = plan.directives("Periapsis").collect();
    if (periapsisActs.isEmpty()) {
      System.out.println("[StopConditions] No Periapsis activities found - cannot schedule radar");
      plan.commit();
      return;
    }

    // Create orbit windows based on periapsis times
    ArrayList<Pair<Instant, Instant>> windows = new ArrayList<>();
    for (int i = 0; i < periapsisActs.size(); i = i + 2) {
      Instant start = plan.toAbsolute(periapsisActs.get(i).getStartTime());
      Instant end;
      if (i + 1 < periapsisActs.size()) {
        end = plan.toAbsolute(periapsisActs.get(i + 1).getStartTime());
      } else {
        end = planEnd;
      }
      windows.add(Pair.of(start, end));
    }

    // Tracking statistics
    int scheduled = 0;
    int skippedBattery = 0;
    int skippedData = 0;

    long VISAR_ORBIT_SEGMENTS = 8;
    boolean firstObs = true;
    boolean radarTurnedOff = false;
    Instant lastRadarActivityTime = null;

    for (int i = 0; i < windows.size(); i++) {
      int sciOrDl = i % (NUM_SCI_ORBITS + NUM_DL_ORBITS);

      if (sciOrDl < NUM_SCI_ORBITS) {
        Instant orbStartTime = windows.get(i).getLeft();

        // Check stop conditions at the start of this orbit
        StopConditionResult stopCheck = checkStopConditions(sim, plan.toRelative(orbStartTime));

        if (!stopCheck.batteryOK) {
          System.out.println("[StopConditions] Orbit " + i + ": SKIPPED - Battery SOC (" +
              String.format("%.1f", stopCheck.batteryValue) + "%) below minimum (" + minBatterySOC + "%)");
          skippedBattery++;
          // Turn radar off if it's on (to stop consuming power)
          if (!firstObs && !radarTurnedOff && lastRadarActivityTime != null) {
            turnRadarOff(plan, lastRadarActivityTime, "low battery");
            radarTurnedOff = true;
            sim = plan.simulate();
          }
          continue;
        }

        if (!stopCheck.dataOK) {
          System.out.println("[StopConditions] Orbit " + i + ": SKIPPED - Data volume (" +
              String.format("%.2f", stopCheck.dataValue / 1e9) + " Gb) exceeds maximum (" +
              String.format("%.2f", maxDataVolumeBits / 1e9) + " Gb)");
          skippedData++;
          // Turn radar off if it's on (to stop generating data)
          if (!firstObs && !radarTurnedOff && lastRadarActivityTime != null) {
            turnRadarOff(plan, lastRadarActivityTime, "high data volume");
            radarTurnedOff = true;
            sim = plan.simulate();
          }
          continue;
        }

        // Conditions OK - proceed with scheduling
        System.out.println("[StopConditions] Orbit " + i + ": OK - Battery=" +
            String.format("%.1f", stopCheck.batteryValue) + "%, Data=" +
            String.format("%.2f", stopCheck.dataValue / 1e9) + " Gb");

        // Turn on VISAR if this is the first observation OR if radar was turned off
        if (firstObs || radarTurnedOff) {
          Instant warmupTime = instantMinusDuration(orbStartTime, RADAR_WARMUP_DUR);
          // For first observation, don't go before plan start
          // For subsequent observations, don't go before last radar activity
          if (firstObs && warmupTime.isBefore(planStart)) {
            warmupTime = planStart;
          } else if (radarTurnedOff && lastRadarActivityTime != null && warmupTime.isBefore(lastRadarActivityTime)) {
            warmupTime = lastRadarActivityTime;
          }

          String reason = firstObs ? "first observation" : "resuming after stop condition cleared";
          turnRadarOn(plan, warmupTime, reason);

          firstObs = false;
          radarTurnedOff = false;

          // Re-simulate after turning radar on to get updated resource values
          sim = plan.simulate();
        }

        // Schedule radar modes for this orbit (if not the last orbit)
        if (i + 1 != windows.size()) {
          Instant nextOrbStartTime = windows.get(i + 1).getLeft();
          Duration orbSegDur = durationBetweenInstants(orbStartTime, nextOrbStartTime).dividedBy(VISAR_ORBIT_SEGMENTS);
          Instant nextRadarActTime = orbStartTime;

          // First 4 segments: LOW_RES (DEM)
          scheduleRadarMode(plan, nextRadarActTime, RadarDataCollectionMode.LOW_RES);
          nextRadarActTime = instantPlusDuration(nextRadarActTime, orbSegDur.times(4));
          scheduled++;

          // Next 3 segments: MED_RES
          scheduleRadarMode(plan, nextRadarActTime, RadarDataCollectionMode.MED_RES);
          nextRadarActTime = instantPlusDuration(nextRadarActTime, orbSegDur.times(3));
          scheduled++;

          // Final segment: HI_RES
          scheduleRadarMode(plan, nextRadarActTime, RadarDataCollectionMode.HI_RES);
          nextRadarActTime = instantPlusDuration(nextRadarActTime, orbSegDur.times(1));
          scheduled++;
          lastRadarActivityTime = nextRadarActTime;

          // Turn VISAR off if this is the last science orbit before downlink
          if (sciOrDl + 1 == NUM_SCI_ORBITS) {
            scheduleRadarMode(plan, nextRadarActTime, RadarDataCollectionMode.OFF);

            var offDirective = new NewDirective(
                new AnyDirective(Map.of()),
                "Radar_Off",
                "Radar_Off",
                new DirectiveStart.Absolute(plan.toRelative(nextRadarActTime)));
            plan.create(offDirective);
            radarTurnedOff = true;
          }

          // Re-simulate after adding activities to get updated resource values
          sim = plan.simulate();
        }
      }
    }

    // Turn radar off if we scheduled activities but didn't turn it off already
    if (!firstObs && !radarTurnedOff && lastRadarActivityTime != null) {
      System.out.println("[StopConditions] Turning radar off after early stop");
      scheduleRadarMode(plan, lastRadarActivityTime, RadarDataCollectionMode.OFF);

      var offDirective = new NewDirective(
          new AnyDirective(Map.of()),
          "Radar_Off",
          "Radar_Off",
          new DirectiveStart.Absolute(plan.toRelative(lastRadarActivityTime)));
      plan.create(offDirective);
    }

    // Summary
    System.out.println("[StopConditions] === SUMMARY ===");
    System.out.println("[StopConditions] Scheduled: " + scheduled + " radar mode changes");
    System.out.println("[StopConditions] Skipped (low battery): " + skippedBattery + " orbits");
    System.out.println("[StopConditions] Skipped (high data volume): " + skippedData + " orbits");

    plan.commit();
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
   * Helper to schedule a radar mode change activity.
   */
  private void scheduleRadarMode(EditablePlan plan, Instant time, RadarDataCollectionMode mode) {
    Map<String, SerializedValue> actArgs = Map.of(
        "mode", new EnumValueMapper<>(RadarDataCollectionMode.class).serializeValue(mode));

    var directive = new NewDirective(
        new AnyDirective(actArgs),
        "ChangeRadarDataMode",
        "ChangeRadarDataMode",
        new DirectiveStart.Absolute(plan.toRelative(time)));
    plan.create(directive);
  }

  /**
   * Helper to turn radar off at the specified time.
   */
  private void turnRadarOff(EditablePlan plan, Instant time, String reason) {
    System.out.println("[StopConditions] Turning radar off: " + reason);
    scheduleRadarMode(plan, time, RadarDataCollectionMode.OFF);

    var offDirective = new NewDirective(
        new AnyDirective(Map.of()),
        "Radar_Off",
        "Radar_Off",
        new DirectiveStart.Absolute(plan.toRelative(time)));
    plan.create(offDirective);
  }

  /**
   * Helper to turn radar on at the specified time.
   */
  private void turnRadarOn(EditablePlan plan, Instant time, String reason) {
    System.out.println("[StopConditions] Turning radar on: " + reason);

    var onDirective = new NewDirective(
        new AnyDirective(Map.of()),
        "Radar_On",
        "Radar_On",
        new DirectiveStart.Absolute(plan.toRelative(time)));
    plan.create(onDirective);
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
