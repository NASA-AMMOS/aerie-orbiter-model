package constraints.procedures;

import gov.nasa.ammos.aerie.procedural.constraints.Constraint;
import gov.nasa.ammos.aerie.procedural.constraints.Violations;
import gov.nasa.ammos.aerie.procedural.constraints.annotations.ConstraintProcedure;
import gov.nasa.ammos.aerie.procedural.timeline.plan.Plan;
import gov.nasa.ammos.aerie.procedural.timeline.plan.SimulationResults;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

@ConstraintProcedure
public record DownlinkMinDuration(Duration minDur) implements Constraint {
  @Override
  public Violations run(Plan plan, SimulationResults simResults) {
    return Violations.on(
      simResults.instances("Downlink").filterShorterThan(minDur).active(),
      true);
  }
}
