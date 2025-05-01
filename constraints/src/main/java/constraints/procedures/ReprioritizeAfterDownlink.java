package constraints.procedures;

import gov.nasa.ammos.aerie.procedural.constraints.Constraint;
import gov.nasa.ammos.aerie.procedural.constraints.Violations;
import gov.nasa.ammos.aerie.procedural.constraints.annotations.ConstraintProcedure;
import gov.nasa.ammos.aerie.procedural.timeline.Interval;
import gov.nasa.ammos.aerie.procedural.timeline.collections.Windows;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.AnyInstance;
import gov.nasa.ammos.aerie.procedural.timeline.payloads.activities.Instance;
import gov.nasa.ammos.aerie.procedural.timeline.plan.Plan;
import gov.nasa.ammos.aerie.procedural.timeline.plan.SimulationResults;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

import java.util.List;

@ConstraintProcedure
public record ReprioritizeAfterDownlink(Duration maxOffset) implements Constraint {
  @Override
  public Violations run(Plan plan, SimulationResults simResults) {

    List<Instance<AnyInstance>> parentActs  = simResults.instances("Downlink").collect();

    Windows opponentWins = simResults.instances("ReprioritizeData").highlightAll();

    // Collector of violation windows
    Windows violationWins = new Windows();

    // For each parent activity, determine if there is an opponent activity (or activities) within
    // the time between the end of the parent activity and the maxOffset. If not, report a violation
    for (Instance<AnyInstance> parent : parentActs) {
      Windows maxOffsetWin = new Windows(Interval.between(parent.getInterval().end, parent.getInterval().end.plus(maxOffset)));
      Windows offsetIntersect = maxOffsetWin.intersection(opponentWins);
      if (offsetIntersect.collect().isEmpty()) {
        // Collect violations
        violationWins = violationWins.union(maxOffsetWin);
      }

    }
    return Violations.inside(violationWins);
  }
}
