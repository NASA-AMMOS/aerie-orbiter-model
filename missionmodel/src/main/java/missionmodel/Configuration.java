package missionmodel;

import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import missionmodel.power.PowerModelSimConfig;

import java.nio.file.Path;
import java.util.Map;

import static gov.nasa.jpl.aerie.merlin.framework.annotations.Export.Template;

public record Configuration(Integer spiceSpacecraftId, PowerModelSimConfig powerConfig, Double offPointAngle) {

  public static final Integer DEFAULT_SPICE_SCID = -74; // MRO

  public static final PowerModelSimConfig POWER_CONFIG = PowerModelSimConfig.defaultConfiguration();

  public static final Double DEFAULT_OFF_POINT_ANGLE = 70.0; // Worst case off point

  public static @Template Configuration defaultConfiguration() {
    return new Configuration(DEFAULT_SPICE_SCID, POWER_CONFIG, DEFAULT_OFF_POINT_ANGLE);
  }
}
