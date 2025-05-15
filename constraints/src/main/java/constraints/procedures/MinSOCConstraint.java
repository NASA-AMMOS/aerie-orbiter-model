package constraints.procedures;

import gov.nasa.ammos.aerie.procedural.constraints.Constraint;
import gov.nasa.ammos.aerie.procedural.constraints.Violations;
import gov.nasa.ammos.aerie.procedural.constraints.annotations.ConstraintProcedure;
import gov.nasa.ammos.aerie.procedural.timeline.collections.profiles.Real;
import gov.nasa.ammos.aerie.procedural.timeline.plan.Plan;
import gov.nasa.ammos.aerie.procedural.timeline.plan.SimulationResults;

@ConstraintProcedure
public record MinSOCConstraint(double threshold) implements Constraint {
  @Override
  public Violations run(Plan plan, SimulationResults simResults) {
    final var soc = simResults.resource("cbebattery.batterySOC", Real.deserializer());

    return Violations.on(
      soc.lessThan(threshold),
      true
    );
  }
}
