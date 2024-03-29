package missionmodel.telecom;
import gov.nasa.jpl.aerie.contrib.streamline.unit_aware.UnitAware;
import gov.nasa.jpl.aerie.merlin.framework.annotations.AutoValueMapper;

import java.util.List;

import static gov.nasa.jpl.aerie.contrib.streamline.unit_aware.StandardUnits.WATT;

public class TelecomValueMappers {
//  @AutoValueMapper.Record
//  public record TelecomConfig(
//    List<AntennaConfig> antennae
//  ) {}
//
//  // TODO pointing loss function. Table representing beam pattern
//  @AutoValueMapper.Record
//  public record AntennaConfig<BodyId>(
//    String name,
//    AntennaType type,
//    /// double communicationEfficiency, // Not MVP
//    UnitAware<Double> size, // diameter, meters
//    List<BandPower> powerByBand, // amplifier specs
//    BodyId bodyId // for geometric calculations
//  ) {
//  }
//
//  @AutoValueMapper.Record
//  public record BandPower(TelecomModel.Band band, UnitAware<Double> power) {
//    public BandPower {
//      power.in(WATT); // unit checking
//    }
//  }
//
//  public enum AntennaType {
//    HIGH_GAIN, // DSN antennae are all high gain
//    MEDIUM_GAIN,
//    LOW_GAIN,
//    FAN_BEAM // This is the hardest to compute pointing loss for
//  }
}
