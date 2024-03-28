package missionmodel.geometry.interfaces;

import missionmodel.geometry.returnedobjects.RADec;
import missionmodel.geometry.returnedobjects.IlluminationAngles;
import missionmodel.geometry.returnedobjects.OrbitConicElements;
import missionmodel.geometry.returnedobjects.SubPointInformation;
import gov.nasa.jpl.time.Time;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public interface TimeDependentStateCalculator {

  /**
   *
   * @param et The time at which the desired information is requested The time at which the desired information is requested
   * @param observer The target and observer define a state vector whose position component points from the observer to the target
   * @param target See 'observer'
   * @param abcorr Aberration correction that should be applied when computing state
   * @return A two-length array of Vector3D objects. The first contains the position vector from observer to target (in km), and the second contains the velocity vector of the target with respect to the observer (in km/s)
   * @throws GeometryInformationNotAvailableException
   */
  Vector3D[] getState(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param observer One of the two bodies
   * @param target One of the two bodies
   * @param abcorr Aberration correction that should be applied when computing state
   * @return The distance between the observer and the target center, in km
   * @throws GeometryInformationNotAvailableException
   */
  double getRange(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param observer One of the two bodies
   * @param target One of the two bodies
   * @param abcorr Aberration correction that should be applied when computing state
   * @return The relative speed of the two bodies, in km/s
   * @throws GeometryInformationNotAvailableException
   */
  double getSpeed(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param spacecraft The object whose altitude above the surface is requested
   * @param body The body whose surface the altitude is measured over
   * @param abcorr Aberration correction that should be applied when computing state
   * @param useDSK Whether a DSK should be used for 'body' to measure altitude against
   * @return The altitude of the spacecraft over the body surface (not center), in km
   * @throws GeometryInformationNotAvailableException
   */
  double getSpacecraftAltitude(Time et, String spacecraft, String body, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param spacecraft The spacecraft name or ID
   * @param body The body that is at the vertex of the requested angle
   * @param abcorr Aberration correction that should be applied when computing state
   * @return The angle (in degrees) of the sun and spacecraft as seen by 'body'
   * @throws GeometryInformationNotAvailableException
   */
  double getSunBodySpacecraftAngle(Time et, String spacecraft, String body, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param spacecraft The spacecraft name or ID, at the vertex of the angle
   * @param body The other body the spacecraft is observing besides the sun
   * @param abcorr Aberration correction that should be applied when computing state
   * @return The angle (in degrees) of the sun and the 'body' as seen by the spacecraft
   * @throws GeometryInformationNotAvailableException
   */
  double getSunSpacecraftBodyAngle(Time et, String spacecraft, String body, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param spacecraft The spacecraft name or ID, at the vertex of the angle
   * @param body The other body the spacecraft is observing besides the earth
   * @param abcorr Aberration correction that should be applied when computing state
   * @return The angle (in degrees) of the earth and the 'body' as seen by the spacecraft
   * @throws GeometryInformationNotAvailableException
   */
  double getEarthSpacecraftBodyAngle(Time et, String spacecraft, String body, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param spacecraft The spacecraft name or ID
   * @param abcorr Aberration correction that should be applied when computing state
   * @return The angle (in degrees) of the earth and spacecraft as seen by the sun
   * @throws GeometryInformationNotAvailableException
   */
  double getEarthSunProbeAngle(Time et, String spacecraft, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param observer The observer whose subpoint the information is being computed about
   * @param target The body on which the subpoint is falling
   * @param abcorr Aberration correction that should be applied when computing state
   * @param useDSK Whether a DSK should be used for 'target' when computing subpoint
   * @return A SubPointInformation object containing among other fields a Vector3D 'spoint' that can be converted to lat/lon coordinates
   * @throws GeometryInformationNotAvailableException
   */
  SubPointInformation getSubPointInformation(Time et, String observer, String target, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param observer The observer which is experiencing the calculated illumination angles
   * @param target The body which is being illuminated by the sun
   * @param abcorr Aberration correction that should be applied when computing state
   * @param useDSK Whether a DSK should be used for 'target' when computing illumination angles
   * @return An IlluminationAngles object that contains phase, incidence, and emission angle fields (in degrees)
   * @throws GeometryInformationNotAvailableException
   */
  IlluminationAngles getIlluminationAngles(Time et, String observer, String target, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param observer The orbiting body
   * @param target The center body
   * @param abcorr Aberration correction that should be applied when computing state
   * @return An OrbitConicElements which has fields for each element. Units are rad, rad/s, and km
   * @throws GeometryInformationNotAvailableException
   */
  OrbitConicElements getOrbitConicElements(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param observer The orbiting body
   * @param body The center body
   * @param abcorr Aberration correction that should be applied when computing state
   * @return The beta angle of the observer around the body, in degrees
   * @throws GeometryInformationNotAvailableException
   */
  double getBetaAngle(Time et, String observer, String body, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param spacecraft The observer
   * @param body The body
   * @param abcorr Aberration correction that should be applied when computing state
   * @return The body half-angle as seen by the spacecraft, in degrees
   * @throws GeometryInformationNotAvailableException
   */
  double getBodyHalfAngleSize(Time et, String spacecraft, String body, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param observer  The viewer that is measuring the RA and Dec
   * @param target The RA and Dec of this object are returned
   * @param abcorr Aberration correction that should be applied when computing state
   * @return An RADec object that has fields for right ascension and declination, both in degrees
   * @throws GeometryInformationNotAvailableException
   */
  RADec getRADec(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param et The time at which the desired information is requested
   * @param spacecraft The observer at whose subpoint the LST is requested
   * @param body The body on which a point's LST is requested
   * @param abcorr Aberration correction that should be applied when computing state
   * @param useDSK Whether a DSK should be used for 'body' in the calculation of LST
   * @return The local solar time-of-day, in hours past local midnight
   * @throws GeometryInformationNotAvailableException
   */
  double getLST(Time et, String spacecraft, String body, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException;

}
