package missionmodel.geometry.returnedobjects;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import spice.basic.CSPICE;
import spice.basic.SpiceErrorException;

public class LatLonCoord {
  private double radius;
  private double longitude;
  private double latitude;

  public LatLonCoord(Vector3D rectangular) {
    this(rectangular.toArray());
  }

  /**************************************************************************************
   * Calculates lattitude, longitude, and radius based off of given rectangular coordinates.
   * Potential replacement for reclat_apiok.
   * @param recArray     - Vector3D for x, y, and z coordinates.
   **************************************************************************************/
  public LatLonCoord(double[] recArray) {
    double d1 = Math.abs(recArray[0]);
    double d2 = Math.abs(recArray[1]);
    d1 = Math.max(d1, d2);
    d2 = Math.abs(recArray[2]);
    double big = Math.max(d1, d2);
    double x, y, z;
    if (big > 0.) {
      x = recArray[0] / big;
      y = recArray[1] / big;
      z = recArray[2] / big;
      radius = big * Math.sqrt(x * x + y * y + z * z);
      latitude = Math.atan2(z, Math.sqrt(x * x + y * y));
      if (recArray[0] == 0. && recArray[1] == 0.) {
        longitude = 0.;
      }
      else {
        longitude = Math.atan2(recArray[1], recArray[0]);
      }
    }
  }

  public LatLonCoord(double rad, double lon, double lat) {
    radius = rad;
    longitude = lon;
    latitude = lat;
  }

  public Vector3D toRectangular(){
    return new Vector3D(
      radius*Math.cos(latitude)*Math.cos(longitude),
      radius*Math.cos(latitude)*Math.sin(longitude),
      radius*Math.sin(latitude)
    );
  }

  public double getRadius() {
    return radius;
  }

  public double getLongitude() {
    return longitude;
  }

  public double getLatitude() {
    return latitude;
  }
}
