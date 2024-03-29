package missionmodel.telecom;

import gov.nasa.jpl.aerie.contrib.serialization.mappers.DoubleValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.DoubleValueMapper;
import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.Registrar;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete;
import gov.nasa.jpl.aerie.contrib.streamline.unit_aware.Unit;
import gov.nasa.jpl.aerie.contrib.streamline.unit_aware.UnitAware;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource.resource;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete.discrete;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects.set;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteResources.unitAware;
import static gov.nasa.jpl.aerie.contrib.streamline.unit_aware.Quantities.quantity;
import static gov.nasa.jpl.aerie.contrib.streamline.unit_aware.StandardUnits.*;
import static gov.nasa.jpl.aerie.contrib.streamline.unit_aware.Unit.derived;
import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.*;
import static missionmodel.telecom.LinkModel.getBitRate;

public class TelecomModel {
    /*
    Options:
    1. Get me the next pass
    2. Get me all passes in a given time range, calculate data rate and duration

    getPassProperties(passId, antenna, startTime, lat, lon) -> passProperties
    getPasses(antenna, window, lat, lon) -> arraylist(passProperties)
    For the "Hello World", a random function will create passes and their properties using the following configurable parameters: number of orbiters, datarate range, duration range
    The model should be able to use kml spice kernels.
     */

    /*
    TODO:
     - Downlink Activity
     - Notion of DSN
     - Geometry: calculate view periods
     - Modes of the telecom subsystem
     - Rely on link equation for bitrate
     */

  //public final Map<CommunicationConfiguration, UnitAware<MutableResource<Discrete<Double>>>> downlinkBitRateCapability;
  public MutableResource<Discrete<Double>> downlinkBitRate;

  // The DSN is one of several providers of antennae. Most importantly, we need the locations of antennae
  private static final Unit CENTIMETER = derived("cm", "centimeter", 0.01, METER);
  private static final Unit MILLIMETER = derived("mm", "millimeter", 0.1, CENTIMETER);
  private static final Unit NANOMETER = derived("nm", "nanometer", 1e-7, CENTIMETER);

  //private final Map<String, TelecomValueMappers.AntennaConfig<String>> antennaeByName;
  //private final ArrayList<CommunicationConfiguration> communicationConfigurations;
  // private final GeometryModel<String> geometryModel;

  //public TelecomModel(final GeometryModel<String> geometryModel, List<TelecomValueMappers.AntennaConfig<String>> spacecraftAntennae, List<TelecomValueMappers.AntennaConfig<String>> otherAntennae) {
  public TelecomModel() {
    this.downlinkBitRate = resource(discrete(0.0));
  }
//    this.antennaeByName = new HashMap<>();
//    spacecraftAntennae.forEach($ -> this.antennaeByName.put($.name(), $));
//    otherAntennae.forEach($ -> this.antennaeByName.put($.name(), $));
//
//    this.communicationConfigurations = new ArrayList<>();
//    for (final var transmittingAntenna : spacecraftAntennae) {
//      for (final var receivingAntenna : otherAntennae) {
//        for (final var commonBand : getCommonBands(transmittingAntenna, receivingAntenna)) {
//          this.communicationConfigurations.add(new CommunicationConfiguration(transmittingAntenna.name(), receivingAntenna.name(), commonBand.band()));
//        }
//      }
//    }
//
//    // Responsible for computing downlinkBitRateCapability for each pair of antennae
//    this.downlinkBitRateCapability = new HashMap<>();
//    for (final var config : communicationConfigurations) {
//      this.downlinkBitRateCapability.put(config, unitAware(resource(discrete(0.0)), MEGABIT_PER_SECOND));
//    }
//
//    spawn(replaying(() -> daemon(Duration.of(1, Duration.HOUR))));
//  }
//
//  public record PassProperties(String passId, String receiverAntennaId, UnitAware<Double> dataRate, Instant startTime, Duration duration) {
//    public PassProperties {
//      dataRate.in(MEGABIT_PER_SECOND);
//    }
//  }
//
//  public record CommunicationConfiguration(String transmitter, String receiver, Band band) {}
//
//  private record TransmitterReceiverBandPower(Band band, UnitAware<Double> transmitterPower, UnitAware<Double> receiverPower) {}
//
//  public PassProperties getPassProperties(String passId, String transmittingAntenna) {
//    return new PassProperties(
//      passId,
//      "",
//      quantity(0.0, MEGABIT_PER_SECOND),
//      Instant.EPOCH,
//      Duration.ZERO
//    );
//  }
//
//  public List<PassProperties> getPasses(String transmittingAntenna, Instant startTime, Duration duration) {
//    final var res = new ArrayList<PassProperties>();
//    for (final var commConfig : communicationConfigurations) {
//      if (!commConfig.transmitter().equals(transmittingAntenna)) continue;
//
//      final var viewPeriods = geometryModel.getViewPeriods(commConfig.transmitter(), commConfig.receiver(), startTime, duration, quantity(0, METER));
//      for (final var viewPeriod : viewPeriods) {
//        res.add(new PassProperties(
//          UUID.randomUUID().toString(),
//          commConfig.receiver(),
//          computeBitRate(commConfig, viewPeriod.averageDistance()),
//          viewPeriod.startTimeTransmitter(),
//          viewPeriod.duration()
//        ));
//      }
//    }
//    return res;
//  }
//
//  // TODO consider how to reflect higher fidelity points within this sample period, to be coherent
//  public void daemon(final Duration samplePeriod) {
//    for (final var config : communicationConfigurations) {
//      final var isVisible = geometryModel.isVisible(config.transmitter(), config.receiver());
//      final var distance = geometryModel.getDistanceBetween(config.transmitter(), config.receiver());
//      if (!isVisible) {
//        set(this.downlinkBitRateCapability.get(config), quantity(0.0, MEGABIT_PER_SECOND));
//      } else {
//        set(this.downlinkBitRateCapability.get(config), computeBitRate(config, distance).in(MEGABIT_PER_SECOND));
//      }
//    }
//
//    delay(samplePeriod);
//    spawn(replaying(() -> daemon(samplePeriod)));
//  }
//
//  public UnitAware<Double> computeBitRate(CommunicationConfiguration config, UnitAware<Double> distance) {
//    final var transmittingAntenna = antennaeByName.get(config.transmitter());
//    final var receivingAntenna = antennaeByName.get(config.receiver()); // TODO bitrate should depend on receiving antenna gain, right?
//
//    // Constants
//    final var atmosphericLoss = scalar(1.0); // CONSTANT
//    final var desiredSignalToNoiseRatio = scalar(10e-5); // CONSTANT
//    final var systemTemperature = quantity(150, KELVIN); // Uh...
//
//    final var communicationSystemEfficiency = scalar(1.0); // ENUM
//    final var transmittingAntennaGain = scalar(1.64); // ENUM
//
//    final var receivingAntennaGain = scalar(50); // ENUM
//    final UnitAware<Double> bitRate;
//
//    final var pointingErrorLoss = scalar(1.0); // assume perfect pointing
//
//    final var spaceLoss = spaceLoss(config.band().wavelength, distance);
//
//    bitRate = getBitRate(
//      lookupPower(transmittingAntenna, config.band()),
//      communicationSystemEfficiency,
//      transmittingAntennaGain,
//      spaceLoss,
//      atmosphericLoss,
//      pointingErrorLoss,
//      receivingAntennaGain,
//      systemTemperature,
//      desiredSignalToNoiseRatio
//    );
//    return bitRate;
//  }
//
//  private static UnitAware<Double> lookupPower(final TelecomValueMappers.AntennaConfig<String> transmittingAntenna, final Band band) {
//    for (final var bandPower : transmittingAntenna.powerByBand()) {
//      if (bandPower.band() == band) {
//        return bandPower.power();
//      }
//    }
//    throw new IllegalArgumentException("Antenna " + transmittingAntenna.name() + " does not support the following band: " + band);
//  }
//
//  private static List<TransmitterReceiverBandPower> getCommonBands(
//    final TelecomValueMappers.AntennaConfig<String> transmittingAntenna,
//    final TelecomValueMappers.AntennaConfig<String> receivingAntenna
//  ) {
//    final var res = new ArrayList<TransmitterReceiverBandPower>();
//    for (final var tranmittingBand : transmittingAntenna.powerByBand()) {
//      for (final var receivingBand : receivingAntenna.powerByBand()) {
//        if (tranmittingBand.band() == receivingBand.band()) {
//          res.add(new TransmitterReceiverBandPower(tranmittingBand.band(), tranmittingBand.power(), receivingBand.power()));
//        }
//      }
//    }
//    return res;
//  }

  public void registerResources(final Registrar registrar) {
    registrar.discrete("downlinkBitRate", downlinkBitRate, new DoubleValueMapper()); // actually occurring
//    for (final var entry : this.downlinkBitRateCapability.entrySet()) {
//      final var communicationConfig = entry.getKey();
//      final var resource = entry.getValue();
//      registrar.discrete("/telecom/downlinkBitRateCapability/" + communicationConfig.transmitter + "->" + communicationConfig.receiver + "[" + communicationConfig.band() + "]", resource.value(), new DoubleValueMapper()); // theoretical maximum
//    }
  }

//  public enum Band {
//    UHF(quantity(75, CENTIMETER)),
//    L_BAND(quantity(22.5, CENTIMETER)),
//    S_BAND(quantity(1495, NANOMETER)),
//    C_BAND(quantity(1547.5, NANOMETER)),
//    X_BAND(quantity(3.125, CENTIMETER)),
//    KU_BAND(quantity(20.85, MILLIMETER)),
//    KA_BAND(quantity(0.925, CENTIMETER));
//    public final UnitAware<Double> wavelength;
//
//    Band(UnitAware<Double> wavelength) {
//      this.wavelength = wavelength;
//    }
//  }
}
