package missionmodel.geometry.returnedobjects;

import gov.nasa.jpl.time.Time;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import spice.basic.CSPICE;
import spice.basic.SpiceErrorException;

public class OrbitConicElements {
  private double perifocalDistance;
  private double eccentricity;
  private double inclination;
  private double longitudeOfAscendingNode;
  private double argumentOfPeriapsis;
  private double meanAnomalyAtEpoch;
  private double epoch;
  private double mu;

  public OrbitConicElements(Vector3D bodyRelativePosition, Vector3D bodyRelativeSpeed, Time et, double mu) throws SpiceErrorException {
    double[] conicElements = CSPICE.oscelt(ArrayUtils.addAll(bodyRelativePosition.toArray(), bodyRelativeSpeed.toArray()), et.toET(), mu);
    this.perifocalDistance = conicElements[0];
    this.eccentricity = conicElements[1];
    this.inclination = conicElements[2];
    this.longitudeOfAscendingNode = conicElements[3];
    this.argumentOfPeriapsis = conicElements[4];
    this.meanAnomalyAtEpoch = conicElements[5];
    this.epoch = conicElements[6];
    this.mu = conicElements[7];
  }

  public OrbitConicElements(double perifocalDistance, double eccentricity, double inclination, double longitudeOfAscendingNode,
                            double argumentOfPeriapsis, double meanAnomalyAtEpoch, double epoch, double mu) {
    this.perifocalDistance = perifocalDistance;
    this.eccentricity = eccentricity;
    this.inclination = inclination;
    this.longitudeOfAscendingNode = longitudeOfAscendingNode;
    this.argumentOfPeriapsis = argumentOfPeriapsis;
    this.meanAnomalyAtEpoch = meanAnomalyAtEpoch;
    this.epoch = epoch;
    this.mu = mu;
  }

  public double getPerifocalDistance() {
    return perifocalDistance;
  }

  public double getEccentricity() {
    return eccentricity;
  }

  public double getInclination() {
    return inclination;
  }

  public double getLongitudeOfAscendingNode() {
    return longitudeOfAscendingNode;
  }

  public double getArgumentOfPeriapsis() {
    return argumentOfPeriapsis;
  }

  public double getMeanAnomalyAtEpoch() {
    return meanAnomalyAtEpoch;
  }

  public double getEpoch() {
    return epoch;
  }

  public double getMu() {
    return mu;
  }

  public double getOrbitPeriod(){
    double semiMajorAxis = perifocalDistance / (1 - eccentricity);
    return 2 * Math.PI * Math.sqrt(Math.pow(semiMajorAxis, 3) / mu);
  }
}
