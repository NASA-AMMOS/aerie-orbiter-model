package missionmodel.geometry.returnedobjects;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import static missionmodel.geometry.resources.GenericGeometryResources.FLOAT_EPSILON;

public class RADec {
  // degrees
  protected Double RA;
  protected Double Dec;

  /**
   * Calculates the declination of a target as seen by a specified observer
   *
   * @param targetPositionVector   The location of the target in the J2000 frame
   * @param observerPositionVector The location of the observer in the J2000 frame
   * Here, you can pass in (0, 0, 0) for the observer position vector for results in an inertial reference frame
   */
  public RADec(Vector3D targetPositionVector, Vector3D observerPositionVector){
    Vector3D difference = targetPositionVector.subtract(observerPositionVector);
    if (difference.getNorm() > FLOAT_EPSILON) {
      this.RA = Math.atan2(difference.getY(),  difference.getX()   )*(180/Math.PI);
      this.Dec = Math.asin(difference.getZ() / difference.getNorm())*(180/Math.PI);
    }
    else {
      this.RA = 0.0;
      this.Dec = 0.0;
    }
  }

  // parameters in degrees
  public RADec (Double ra, Double dec) {
    this.RA = ra;
    this.Dec = dec;
  }

  /**
   * @return Right ascension in degrees
   */
  public Double getRA() {
    return RA;
  }

  /**
   * @return Declination in degrees
   */
  public Double getDec() {
    return Dec;
  }

  /**
   * @return The same point this RADec coordinate is expressing, but as a rectangular 3D vector
   */
  public Vector3D getRectangular(){
    return new Vector3D( Math.cos(Dec*(Math.PI/180)) * Math.cos(RA*(Math.PI/180)), Math.cos(Dec*(Math.PI/180)) * Math.sin(RA*(Math.PI/180)), Math.sin(Dec*(Math.PI/180)));
  }
}
