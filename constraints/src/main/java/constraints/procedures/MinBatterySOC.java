package constraints.procedures;

import gov.nasa.ammos.aerie.procedural.constraints.Constraint;
import gov.nasa.ammos.aerie.procedural.constraints.Violations;
import gov.nasa.ammos.aerie.procedural.constraints.annotations.ConstraintProcedure;
import gov.nasa.ammos.aerie.procedural.timeline.plan.Plan;
import gov.nasa.ammos.aerie.procedural.timeline.plan.SimulationResults;
import gov.nasa.ammos.aerie.procedural.timeline.collections.profiles.Real;
import gov.nasa.ammos.aerie.procedural.timeline.collections.Windows;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

@ConstraintProcedure
public record MinBatterySOC(double minSOC) implements Constraint {
  @Override
  public Violations run(Plan plan, SimulationResults simResults) {
    // Get windows of time when the radar is ON (since there are many on modes, look for when it is not OFF)
    Windows batteryLowTimes = simResults.resource("cbebattery.batterySOC", Real.deserializer()).lessThan(minSOC).highlightTrue();

    return Violations.inside(batteryLowTimes);
  }
}
