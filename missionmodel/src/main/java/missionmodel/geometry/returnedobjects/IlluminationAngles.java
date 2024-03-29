package missionmodel.geometry.returnedobjects;

import gov.nasa.jpl.time.Time;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import spice.basic.CSPICE;
import spice.basic.SpiceErrorException;

public class IlluminationAngles {
  // all angle stored in degrees
  private double phaseAngle;
  private double incidenceAngle;
  private double emissionAngle;

  public IlluminationAngles(String method, String target, Time et, String fixref, String abcorr, String observer, Vector3D spoint) throws SpiceErrorException {

    double[] trgepc = new double[1];
    double[] srfvec = new double[3];
    double[] angles = new double[3];

    CSPICE.ilumin(method, target, et.toET(), fixref, abcorr, observer, spoint.toArray(), trgepc, srfvec, angles);
    phaseAngle = angles[0]*(180/Math.PI);
    incidenceAngle = angles[1]*(180/Math.PI);
    emissionAngle = angles[2]*(180/Math.PI);
  }

  /**
   *
   * @param phase angle in deg
   * @param incidence angle in deg
   * @param emission angle in deg
   */
  public IlluminationAngles(double phase, double incidence, double emission) {
    phaseAngle = phase;
    incidenceAngle = incidence;
    emissionAngle = emission;
  }

  public double getPhaseAngle() {
    return phaseAngle;
  }

  public double getEmissionAngle() {
    return emissionAngle;
  }

  public double getIncidenceAngle() {
    return incidenceAngle;
  }
}
