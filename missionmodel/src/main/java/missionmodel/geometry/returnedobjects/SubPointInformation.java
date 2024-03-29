package missionmodel.geometry.returnedobjects;

import gov.nasa.jpl.time.Time;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import spice.basic.CSPICE;
import spice.basic.SpiceErrorException;

public class SubPointInformation {
  private Vector3D spoint;
  private Vector3D srfvec;
  private double trgepc;
  private boolean found;

  public SubPointInformation(String method, String target, Time et, String fixref, String abcorr, String observer, String dref, Vector3D dvec) throws SpiceErrorException {

    double[] spointdoubles = new double[3];
    double[] srfvecdoubles = new double[3];
    double[] trgepcdouble = new double[1];
    boolean[] foundArray = new boolean[1];

    CSPICE.sincpt(method, target, et.toET(), fixref, abcorr, observer, dref, dvec.toArray(), spointdoubles, trgepcdouble, srfvecdoubles, foundArray);
    spoint = new Vector3D(spointdoubles);
    srfvec = new Vector3D(srfvecdoubles);
    trgepc = trgepcdouble[0];
    found = foundArray[0];
  }

  public SubPointInformation(Vector3D spoint, Vector3D srfvec, double trgepc, boolean found) {
    this.spoint = spoint;
    this.srfvec = srfvec;
    this.trgepc = trgepc;
    this.found = found;
  }

  public Vector3D getSpoint() {
    return spoint;
  }

  public Vector3D getSrfvec() {
    return srfvec;
  }

  public double getTrgepc() {
    return trgepc;
  }

  public boolean isFound() {
    return found;
  }
}
