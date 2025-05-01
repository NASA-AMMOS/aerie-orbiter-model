package constraints.procedures;

import gov.nasa.ammos.aerie.procedural.constraints.Constraint;
import gov.nasa.ammos.aerie.procedural.constraints.Violations;
import gov.nasa.ammos.aerie.procedural.constraints.annotations.ConstraintProcedure;
import gov.nasa.ammos.aerie.procedural.timeline.Interval;
import gov.nasa.ammos.aerie.procedural.timeline.collections.Windows;
import gov.nasa.ammos.aerie.procedural.timeline.collections.profiles.Strings;
import gov.nasa.ammos.aerie.procedural.timeline.plan.Plan;
import gov.nasa.ammos.aerie.procedural.timeline.plan.SimulationResults;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

@ConstraintProcedure
public record MinWarmupDuration(Duration minDur) implements Constraint {
  @Override
  public Violations run(Plan plan, SimulationResults simResults) {
    // Get windows of time when the radar is ON (since there are many on modes, look for when it is not OFF)
    Windows radarOnTimes = simResults.resource("radarState", Strings.deserializer()).notEqualTo("OFF").highlightTrue();
    // Get windows of time when the data collection is ON
    Windows dataCollectOffTimes = simResults.resource("RadarDataMode", Strings.deserializer()).notEqualTo("OFF").highlightTrue();

    // Collector of violation windows
    Windows violationWins = new Windows();

    // For the first part of each on-time interval up to the minimum duration, the radar should not be collecting data
    // (since it should be warming up).
    for (Interval onTime : radarOnTimes) {
      // Create an interval representing the minimum time data collection should be off
      Interval warmupWin = Interval.between(onTime.start,onTime.start.plus(minDur));
      // Find times within the warmup interval when data collection is ON (our violations)
      Windows tmpViolations = new Windows(warmupWin).intersection(dataCollectOffTimes);
      // Collect violations
      violationWins = violationWins.union(tmpViolations);
    }
    return Violations.inside(violationWins);
  }
}
