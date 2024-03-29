package missionmodel.telecom;

import gov.nasa.jpl.aerie.contrib.streamline.unit_aware.Quantities;
import gov.nasa.jpl.aerie.contrib.streamline.unit_aware.Unit;
import gov.nasa.jpl.aerie.contrib.streamline.unit_aware.UnitAware;

import static gov.nasa.jpl.aerie.contrib.streamline.unit_aware.Quantities.quantity;
import static gov.nasa.jpl.aerie.contrib.streamline.unit_aware.StandardUnits.*;

public class LinkModel {
  private static final Unit UNITLESS = SECOND.divide(SECOND);
  private static final UnitAware<Double> BOLTZMANN_CONSTANT = quantity(1.380649e-23, JOULE.divide(KELVIN));
  public static UnitAware<Double> getBitRate(
    UnitAware<Double> powerInput, // WATT
    UnitAware<Double> communicationSystemEfficiency, // SCALAR
    UnitAware<Double> transmittingAntennaGain, // SCALAR
    UnitAware<Double> spaceLoss, // SCALAR
    UnitAware<Double> atmosphericLoss, // SCALAR
    UnitAware<Double> pointingErrorLoss, // SCALAR
    UnitAware<Double> receivingAntennaGain, // SCALAR,
    UnitAware<Double> systemTemperature, // Kelvin
    UnitAware<Double> desiredSignalToNoiseRatio // SCALAR. is this an input?
  ) {
    // Parameter unit checking
    powerInput.in(WATT);
    communicationSystemEfficiency.in(UNITLESS);
    transmittingAntennaGain.in(UNITLESS);
    spaceLoss.in(UNITLESS);
    atmosphericLoss.in(UNITLESS);
    pointingErrorLoss.in(UNITLESS);
    receivingAntennaGain.in(UNITLESS);
    systemTemperature.in(KELVIN);
    desiredSignalToNoiseRatio.in(UNITLESS);

    final var bandwidth = Quantities.divide(
      multiply(powerInput,
        communicationSystemEfficiency,
        transmittingAntennaGain,
        spaceLoss,
        atmosphericLoss,
        pointingErrorLoss,
        receivingAntennaGain),
      multiply(BOLTZMANN_CONSTANT,
        systemTemperature,
        desiredSignalToNoiseRatio));

    // Assume bandwidth ~ bitrate
    return multiply(bandwidth, quantity(1.0, BIT)).in(MEGABIT_PER_SECOND);
  }

  public static UnitAware<Double> scalar(double value) {
    return quantity(value, UNITLESS);
  }

  private static UnitAware<Double> square(UnitAware<Double> quantity) {
    return multiply(quantity, quantity);
  }

  public static UnitAware<Double> spaceLoss(UnitAware<Double> wavelength, UnitAware<Double> distance) {
    wavelength.in(METER);
    distance.in(METER);

    UnitAware<Double> result = square(Quantities.divide(
      wavelength,
      multiply(scalar(4 * Math.PI), distance)));
    return result.in(UNITLESS);
  }

  @SafeVarargs
  private static UnitAware<Double> multiply(UnitAware<Double> firstValue, UnitAware<Double>... remainingValues) {
    var result = firstValue;
    for (final var value : remainingValues) {
      result = Quantities.multiply(result, value);
    }
    return result;
  }
}
