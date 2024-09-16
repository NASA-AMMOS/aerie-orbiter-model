package missionmodel;

// import gov.nasa.jpl.aerie.contrib.serialization.mappers.DoubleValueMapper;
// import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.DoubleValueMapper;
import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.core.Resource;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.Registrar;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteResources;
import gov.nasa.jpl.time.Duration;
import missionmodel.data.Data;
import missionmodel.data.DataMissionModel;
import missionmodel.geometry.resources.GenericGeometryResources;
import missionmodel.geometry.spiceinterpolation.GenericGeometryCalculator;
import missionmodel.geometry.spiceinterpolation.SpiceResourcePopulater;
import missionmodel.power.BatteryModel;
import missionmodel.power.GenericSolarArray;
import missionmodel.power.pel.PELModel;
import missionmodel.spice.Spice;
import missionmodel.telecom.TelecomModel;
import missionmodel.radar.RadarModel;
import spice.basic.SpiceErrorException;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static gov.nasa.jpl.aerie.contrib.metadata.UnitRegistrar.withUnit;
import static gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource.resource;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete.discrete;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteResources.discreteResource;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteResources.divide;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.PolynomialResources.asPolynomial;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.PolynomialResources.multiply;
import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.spawn;
// import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete;

// import static gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource.resource;
// import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete.discrete;

/**
 * Top-level Mission Model Class
 *
 * Declare, define, and register resources within this class or its delegates
 * Spawn daemon tasks using spawn(objectName::nameOfMethod) within the class constructor
 */
public final class Mission implements DataMissionModel {

  // Special registrar class that handles simulation errors via auto-generated resources
  public final Registrar errorRegistrar;

  // Geometry Member Variables
  public final AbsoluteClock absoluteClock;

  public final GenericGeometryCalculator geometryCalculator;

  public final SpiceResourcePopulater spiceResPop;

  public final GenericGeometryResources geometryResources;

  public static final Path VERSIONED_KERNELS_ROOT_DIRECTORY = Path.of(System.getenv().getOrDefault("SPICE_DIRECTORY", "spice/kernels"));

  public static final String NAIF_META_KERNEL_PATH = VERSIONED_KERNELS_ROOT_DIRECTORY.toString() + "/latest_meta_kernel.tm";

  // Power Member Variables
  public final PELModel pel;
  public GenericSolarArray array;
  public final BatteryModel cbebattery;
  public final BatteryModel mevbattery;

  // Data Member Variables
  /**
   * A resource specifying the spacecraft's data rate for playback to ground in bits per second.
   */
  public final MutableResource<Discrete<Double>> dataRate; // bps

  /**
   * This is a cap on the spacecraft's data storage.
   */
  public final MutableResource<Discrete<Double>> maxVolune; // bits

  /**
   * This is the interface to data Buckets and corresponding resources from the model package.
   */
  public Data data;

  // Telecom Member Variables
  public final TelecomModel telecomModel;

  // VISAR Member Variables
  public final RadarModel radarModel;

  public Mission(final gov.nasa.jpl.aerie.merlin.framework.Registrar registrar, final Instant planStart, final Configuration config) {
    this.errorRegistrar = new Registrar(registrar, Registrar.ErrorBehavior.Log);
    this.absoluteClock = new AbsoluteClock(planStart);

    try {
      Spice.initialize(NAIF_META_KERNEL_PATH);
    }
    catch (SpiceErrorException e) {
      System.out.println(e.getMessage());
    }

    //
    // Initialize Geometry Model
    //
    this.geometryCalculator = new GenericGeometryCalculator(this.absoluteClock, config.spiceSpacecraftId(), "LT+S", this.errorRegistrar);
    // Assume no gaps in SPICE data for now
    this.spiceResPop = new SpiceResourcePopulater(this.geometryCalculator, this.absoluteClock, new Window[]{}, Duration.ZERO_DURATION );
    this.geometryResources = this.geometryCalculator.getResources();
    this.spiceResPop.calculateTimeDependentInformation();

    // Create a derived resource for Sun range that converts from km to AU
    Double AU_TO_KM = 149597870.691;
    Resource<Discrete<Double>> SpacecraftSunRange_AU = divide(geometryResources.SpacecraftBodyRange.get("SUN"),
      DiscreteResources.constant( AU_TO_KM ));
    errorRegistrar.discrete("SpacecraftBodyRange_SUN_AU", SpacecraftSunRange_AU, withUnit("AU", new DoubleValueMapper()));

    //
    // Initialize Attitude Model
    //
    // @todo add an attitude model. For now include a constant resource to represent off-sun angle
    MutableResource<Discrete<Double>> offSunAngle = resource(discrete(config.offPointAngle()));

    //
    // Initialize Power Model
    //
    this.pel = new PELModel();
    this.array = new GenericSolarArray(config.powerConfig().solarArrayConfig(),
      SpacecraftSunRange_AU, offSunAngle, this.geometryResources.FractionOfSunNotInEclipse);
    this.cbebattery = new BatteryModel("cbe", config.powerConfig().batteryConfig(), pel.cbeTotalLoad, array.powerProduction);
    this.mevbattery = new BatteryModel("mev", config.powerConfig().batteryConfig(), pel.mevTotalLoad, array.powerProduction);

    pel.registerStates(this.errorRegistrar);
    array.registerStates(this.errorRegistrar);
    cbebattery.registerStates(this.errorRegistrar);
    mevbattery.registerStates(this.errorRegistrar);

    //
    // Initialize Data Model
    //
    // Two buckets/bins for the spacecraft and two for ground are created here by passing in 2 below.
    // The ground bins track how much data has been played back/downloaded from the spacecraft.
    // bin0 is higher priority than bin 1.
    // The parent bucket has a limit of 10Gb (by default from the Configuration).
    this.dataRate = discreteResource(config.dataConfig().initialDatarate()); // bps
    this.maxVolune = discreteResource(config.dataConfig().initialMaxVolume()); // bits

    this.data = new Data(Optional.of(asPolynomial(dataRate)), 2, asPolynomial(maxVolune));
    data.registerStates(errorRegistrar);

    //
    // Initialize Telecom Model
    //
    this.telecomModel = new TelecomModel();
    telecomModel.registerResources(errorRegistrar);

    //
    // Initialize Radar Model
    //
    this.radarModel = new RadarModel(errorRegistrar, config);
  }

  @Override
  public Data getData() {
    return data;
  }

}
