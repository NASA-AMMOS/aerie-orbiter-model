package constraints.procedures;

import gov.nasa.ammos.aerie.procedural.constraints.Constraint;
import gov.nasa.ammos.aerie.procedural.constraints.Violations;
import gov.nasa.ammos.aerie.procedural.constraints.annotations.ConstraintProcedure;
import gov.nasa.ammos.aerie.procedural.timeline.collections.Windows;
import gov.nasa.ammos.aerie.procedural.timeline.collections.profiles.Real;
import gov.nasa.ammos.aerie.procedural.timeline.plan.Plan;
import gov.nasa.ammos.aerie.procedural.timeline.plan.SimulationResults;

@ConstraintProcedure
public record MaxUnfilteredData(double maxVolume) implements Constraint {
  @Override
  public Violations run(Plan plan, SimulationResults simResults) {
    // get windows of time where onboard data volume is > maxVolume limit
    Windows dataHighTimes = simResults.resource("unfiltered.volume", Real.deserializer()).greaterThan(maxVolume).highlightTrue();

    return Violations.inside(dataHighTimes);
  }
}
