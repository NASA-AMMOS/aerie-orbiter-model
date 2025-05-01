package constraints.procedures;

import gov.nasa.ammos.aerie.procedural.constraints.Constraint;
import gov.nasa.ammos.aerie.procedural.constraints.annotations.ConstraintProcedure;
import gov.nasa.ammos.aerie.procedural.constraints.Violations;
import gov.nasa.ammos.aerie.procedural.timeline.collections.profiles.Booleans;
import gov.nasa.ammos.aerie.procedural.timeline.collections.profiles.Real;
import gov.nasa.ammos.aerie.procedural.timeline.plan.Plan;
import gov.nasa.ammos.aerie.procedural.timeline.plan.SimulationResults;

@ConstraintProcedure
public record NoDownlinkDuringOccultations() implements Constraint {
  @Override
  public Violations run(Plan plan, SimulationResults simResults) {

    Booleans occs = simResults.resource("Occultation", Real.deserializer()).greaterThan(0);
    Booleans downlinks = simResults.instances("Downlink").active();

    return Violations.on(
      occs.and(downlinks),
      true
    );
  }
}
