package missionmodel.geometry.directspicecalls;

import missionmodel.geometry.spiceinterpolation.Body;
import missionmodel.geometry.resources.GenericGeometryResources;
import missionmodel.geometry.interfaces.GeometryInformationNotAvailableException;
import missionmodel.geometry.interfaces.TimeDependentStateCalculator;
import missionmodel.geometry.returnedobjects.*;

import gov.nasa.jpl.time.Time;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import spice.basic.CSPICE;
import spice.basic.SpiceErrorException;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.function.Function;

import static gov.nasa.jpl.time.Duration.*;

public class SpiceDirectTimeDependentStateCalculator implements TimeDependentStateCalculator {

  private Map<String, Body> bodiesMap;
  // Overall map has key of time instant and values of different stored information at that time
  // The inner map has a key of the type of information stored (state, orbit conic elements, illumination angles, etc...) and a value of the conditions defining the info
  // The inner List<Object> has a list of each set of qualifying parameters and then the corresponding return val
  // Ex. List<Object> -> [observer1, target1, abcorr1, state1, observer2, target2, abcorr2, state2, observer3, target3....]
  // Therefore, if a search matches an observer, target, and abcorr for the case above (or the list of parameters in general), then the state in the case above (or the return val) will be returned
  private Map<Time, Map<String, List<Object>>> spiceInfoMap;
  boolean caching;

  public SpiceDirectTimeDependentStateCalculator(boolean cachingOn) {
    this(GenericGeometryResources.getBodies(), cachingOn);
  }

  public SpiceDirectTimeDependentStateCalculator(Map<String, Body> bodiesMap, boolean cachingOn){
    this.bodiesMap = bodiesMap;
    this.spiceInfoMap = new TreeMap<>();
    this.caching = cachingOn;
  }

  public Map<String, Body> getBodiesMap() {
    return this.bodiesMap;
  }

  public Map<Time, Map<String, List<Object>>> getSpiceInfoMap() {
    return this.spiceInfoMap;
  }

  public Object getSpiceInfoValue(Time et, String infoType, Object[] expectedParameters) {
    if (this.spiceInfoMap.containsKey(et)) { // check that values have been cached for this time
      Map<String, List<Object>> spiceInfoAtTime = this.spiceInfoMap.get(et); // get the map of information at that time
      if (spiceInfoAtTime.containsKey(infoType)) { // check if the the provided type of info (Ex. state, range, etc...) has been cached for this time
        List<Object> listOfInfo = spiceInfoAtTime.get(infoType); // get the data from this type of info
        // loop through the sets of information for this type of info at this time
        // += expectedParameters + 1 because each set of information in the list will have the parameters that it needs to equal plus the return value
        // Ex. (observer1, target1, state1, observer2, target2, state2)...therefore parameters are observer and target and return is state
        for (int i = 0; i < listOfInfo.size(); i += (expectedParameters.length + 1)) {
          boolean failMatch = false; // catch if looping through expected parameters fails
          for (int j = 0; j < expectedParameters.length; j++) { // go through and make sure each of the expected parameters match (Ex. observer and target)
            if (!expectedParameters[j].equals(listOfInfo.get(i+j))) { // j gets the jth index of the parameters, i+j is to get the jth index of the current set of information starting at the ith index of the info list
              failMatch = true; // if it does not match the specified parameters, set the boolean and break the for loop cause we only need to look for one fail
              break;
            }
          }
          if (!failMatch) { // if there were no failed match, then the data was actually stored and the return value (which should be the ith index plus the next index after the matching parameters)
            return listOfInfo.get(i+expectedParameters.length);
          }
        }
        return null; // if there was no match success and return in that above loop, then return null
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  public void setSpiceInfoValue(Time et, String infoType, Object[] expectedParameters, Object valueToSet) {
    Map<String, List<Object>> spiceInfoAtTime = new HashMap<>();
    List<Object> listOfInfo = new ArrayList<>();
    if (this.spiceInfoMap.containsKey(et)) { // check that values have been cached for this time
      spiceInfoAtTime = this.spiceInfoMap.get(et); // get the map of information at that time
      if (spiceInfoAtTime.containsKey(infoType)) { // check if the the provided type of info (Ex. state, range, etc...) has been cached for this time
        // don't need to check if there is already a match, because this function will only be called if we're adding new data
        listOfInfo = spiceInfoAtTime.get(infoType); // get the data from this type of info
      }
    }
    for (Object parameter : expectedParameters) {  // manually copying because should not be too many parameters anyways, so it should be not worth the overhead with the collections call
      listOfInfo.add(parameter);
    }
    listOfInfo.add(valueToSet);
    spiceInfoAtTime.put(infoType, listOfInfo);
    this.spiceInfoMap.put(et, spiceInfoAtTime);
  }

  @Override
  public Vector3D[] getState(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException {
    Object[] parameters = new Object[] {observer, target, abcorr};
    if (this.caching) { // check whether we have cached values
      Object returnVal = getSpiceInfoValue(et, "state", parameters);
      if (returnVal != null) { // if we actually got a match for the desired state, then return that, else go to spice call
        return (Vector3D[]) returnVal;
      }
    }
    Vector3D[] returnState = new Vector3D[2];
    try {
      double[] state = new double[6];
      double[] lt = new double[1];
      CSPICE.spkezr(target, et.toET(), "J2000", abcorr, observer, state, lt);
      returnState[0] = new Vector3D(state[0], state[1], state[2]);
      returnState[1] = new Vector3D(state[3], state[4], state[5]);
      if (this.caching) {
        setSpiceInfoValue(et, "state", parameters, returnState);
      }
    } catch (SpiceErrorException e) {
      throw new GeometryInformationNotAvailableException(e.getMessage());
    }
    return returnState; // units are kilometers for distance and kilometers per second for velocity
  }

  @Override
  public double getRange(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException {
    Vector3D positionVector = getState(et, observer, target, abcorr)[0];
    return positionVector.getNorm(); // units are kilometers
  }

  public double getRange(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getRange((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3]);
  }

  @Override
  public double getSpeed(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException {
    Vector3D velocityVector = getState(et, observer, target, abcorr)[1];
    return velocityVector.getNorm(); // units are kilometers per second
  }

  public double getSpeed(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getSpeed((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3]);
  }

  public double getAngleBetweenThreeBodies(Time et, String bodyOne, String centerBody, String bodyTwo, String abcorr) throws GeometryInformationNotAvailableException {
    Vector3D bodyOnePositionWRTcenterBody = getState(et, centerBody, bodyOne, abcorr)[0];
    Vector3D bodyTwoPositionWRTcenterBody = getState(et, centerBody, bodyTwo, abcorr)[0];
    return Vector3D.angle(bodyOnePositionWRTcenterBody, bodyTwoPositionWRTcenterBody)*(180.0/Math.PI); // units are degrees
  }

  @Override
  public double getSunBodySpacecraftAngle(Time et, String spacecraft, String body, String abcorr) throws GeometryInformationNotAvailableException {
    return getAngleBetweenThreeBodies(et, "SUN", body, spacecraft, abcorr); // units are degrees
  }

  public double getSunBodySpacecraftAngle(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getSunBodySpacecraftAngle((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3]);
  }

  @Override
  public double getSunSpacecraftBodyAngle(Time et, String spacecraft, String body, String abcorr) throws GeometryInformationNotAvailableException{
    return getAngleBetweenThreeBodies(et, "SUN", spacecraft, body, abcorr); // units are degrees
  }

  public double getSunSpacecraftBodyAngle(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getSunSpacecraftBodyAngle((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3]);
  }

  @Override
  public double getEarthSpacecraftBodyAngle(Time et, String spacecraft, String body, String abcorr) throws GeometryInformationNotAvailableException {
    return getAngleBetweenThreeBodies(et, "EARTH", spacecraft, body, abcorr); // units are degrees
  }

  public double getEarthSpacecraftBodyAngle(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getEarthSpacecraftBodyAngle((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3]);
  }

  @Override
  public double getEarthSunProbeAngle(Time et, String probe, String abcorr) throws GeometryInformationNotAvailableException {
    return getAngleBetweenThreeBodies(et, "EARTH", "SUN", probe, abcorr); // units are degrees
  }

  public double getEarthSunProbeAngle(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getEarthSunProbeAngle((Time) parameters[0], (String) parameters[1], (String) parameters[2]);
  }

  @Override
  public SubPointInformation getSubPointInformation(Time et, String observer, String target, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException {
    SubPointInformation returnSubPoint;
    Object[] parameters = new Object[] {observer, target, abcorr, useDSK};
    if (this.caching) { // check whether we have cached values
      Object returnVal = getSpiceInfoValue(et, "SubSpacecraftInformation", parameters);
      if (returnVal != null) { // if we actually got a match for the desired sub spacecraft information object, then return that, else go to spice call
        return (SubPointInformation) returnVal;
      }
    }
    try {
      String fixref = getStringFromBodiesMap(target, Body::getNAIFBodyFrame);

      // getState always returns in J2000 so it matches ref frame below
      Vector3D dvec = getState(et, observer, target, abcorr)[0];

      if (useDSK) {
        returnSubPoint = new SubPointInformation("DSK/UNPRIORITIZED", target, et, fixref, abcorr, observer, "J2000", dvec);
      } else {
        returnSubPoint = new SubPointInformation("Ellipsoid", target, et, fixref, abcorr, observer, "J2000", dvec);
      }
      if (this.caching) {
        setSpiceInfoValue(et, "SubSpacecraftInformation", parameters, returnSubPoint);
      }
    } catch (SpiceErrorException e) {
      throw new GeometryInformationNotAvailableException(e.getMessage());
    }
    return returnSubPoint;
  }

  public SubPointInformation getSubPointInformation(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getSubPointInformation((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3], (boolean) parameters[4]);
  }

  @Override
  public IlluminationAngles getIlluminationAngles(Time et, String observer, String target, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException {
    IlluminationAngles returnIlluminationAngles;
    Object[] parameters = new Object[] {observer, target, abcorr, useDSK};
    if (this.caching) { // check whether we have cached values
      Object returnVal = getSpiceInfoValue(et, "IlluminationAngles", parameters);
      if (returnVal != null) { // if we actually got a match for the desired illumination angles object, then return that, else go to spice call
        return (IlluminationAngles) returnVal;
      }
    }
    try {
      String fixref = getStringFromBodiesMap(target, Body::getNAIFBodyFrame);

      Vector3D spoint = getSubPointInformation(et, observer, target, abcorr, useDSK).getSpoint();

      if (useDSK) {
        returnIlluminationAngles = new IlluminationAngles("DSK/UNPRIORITIZED", target, et, fixref, abcorr, observer, spoint);
      } else {
        returnIlluminationAngles = new IlluminationAngles("Ellipsoid", target, et, fixref, abcorr, observer, spoint);
      }
      if (this.caching) {
        setSpiceInfoValue(et, "IlluminationAngles", parameters, returnIlluminationAngles);
      }
    } catch (SpiceErrorException e) {
      throw new GeometryInformationNotAvailableException(e.getMessage());
    }
    return returnIlluminationAngles;
  }

  public IlluminationAngles getIlluminationAngles(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getIlluminationAngles((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3], (boolean) parameters[4]);
  }

  @Override
  public OrbitConicElements getOrbitConicElements(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException {
    double mu = getDoubleFromBodiesMap(target, Body::getMu);

    OrbitConicElements returnOrbitConicElements;
    Vector3D[] stateVector = getInertialState(et, observer, target, abcorr);
    Object[] parameters = new Object[] {observer, target, abcorr, mu};
    if (this.caching) { // check whether we have cached values
      Object returnVal = getSpiceInfoValue(et, "OrbitConicElements", parameters);
      if (returnVal != null) { // if we actually got a match for the desired orbit conic elements object, then return that, else go to spice call
        return (OrbitConicElements) returnVal;
      }
    }
    try {
      returnOrbitConicElements = new OrbitConicElements(stateVector[0], stateVector[1], et, mu);
      if (this.caching) {
        setSpiceInfoValue(et, "OrbitConicElements", parameters, returnOrbitConicElements);
      }
    } catch (SpiceErrorException e) {
      throw new GeometryInformationNotAvailableException(e.getMessage());
    }
    return returnOrbitConicElements;
  }

  public OrbitConicElements getOrbitConicElements(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getOrbitConicElements((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3]);
  }

  @Override
  public double getBetaAngle(Time et, String spacecraft, String body, String abcorr) throws GeometryInformationNotAvailableException {
    Vector3D[] positionAndVelocityVector = getState(et, spacecraft, body, abcorr);
    Vector3D sunPositionWRTBody = getState(et, body, "SUN", abcorr)[0];
    Vector3D orbitPlaneNormal = positionAndVelocityVector[0].crossProduct(positionAndVelocityVector[1]).normalize();
    return Vector3D.angle(orbitPlaneNormal, sunPositionWRTBody.negate())*(180.0/Math.PI)-90; // return value units are degrees
  }

  public double getBetaAngle(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getBetaAngle((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3]);
  }

  @Override
  public double getSpacecraftAltitude(Time et, String spacecraft, String body, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException {
    Vector3D positionVector = getState(et, spacecraft, body, abcorr)[0];
    SubPointInformation sp_sc = getSubPointInformation(et, spacecraft, body, abcorr, useDSK);
    LatLonCoord latLonSurfaceData = new LatLonCoord(sp_sc.getSpoint());

    return positionVector.getNorm()-latLonSurfaceData.getRadius();
  }

  public double getSpacecraftAltitude(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getSpacecraftAltitude((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3], (boolean) parameters[4]);
  }

  @Override
  public double getBodyHalfAngleSize(Time et, String spacecraft, String body, String abcorr) throws GeometryInformationNotAvailableException {
    Vector3D positionVector = getState(et, spacecraft, body, abcorr)[0];
    return Math.asin(getDoubleFromBodiesMap(body, Body::getAverageEquitorialRadius)/positionVector.getNorm())*(180.0/Math.PI); // return value units are in degrees
  }

  public double getBodyHalfAngleSize(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getBodyHalfAngleSize((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3]);
  }

  @Override
  public RADec getRADec(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException {
    Vector3D positionVector = getState(et, observer, target, abcorr)[0];
    return new RADec(positionVector, new Vector3D(0.0,0.0,0.0)); // does not call spice so not needed to cache value to avoid spice calls
  }

  public RADec getRADec(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getRADec((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3]);
  }

  @Override
  public double getLST(Time et, String spacecraft, String body, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException {
    Vector3D positionVector = getState(et, spacecraft, body, abcorr)[0];
    try {
      SubPointInformation sp_sc = getSubPointInformation(et, spacecraft, body, abcorr, useDSK);
      LatLonCoord latLonSurfaceData = new LatLonCoord(sp_sc.getSpoint());
      return et2LSTHours(et, getIntFromBodiesMap(body, Body::getNAIFID), latLonSurfaceData.getLongitude());
    } catch (SpiceErrorException e) {
      throw new GeometryInformationNotAvailableException(e.getMessage());
    }
  }

  public double getLST(Object[] parameters) throws GeometryInformationNotAvailableException {
    return getLST((Time) parameters[0], (String) parameters[1], (String) parameters[2], (String) parameters[3], (boolean) parameters[4]);
  }

  public static double et2LSTHours(Time et, int bodyID, double longitude) throws SpiceErrorException {
    int[] hr = new int[1];
    int[] min = new int[1];
    int[] sec = new int[1];
    String[] time = new String[1];
    String[] ampm = new String[1];
    CSPICE.et2lst(et.toET(), bodyID, longitude, "PLANETOCENTRIC", hr, min, sec, time, ampm);
    return hr[0] + (MINUTE_DURATION.div(HOUR_DURATION)*min[0]) + (SECOND_DURATION.div(HOUR_DURATION)*sec[0]);

  }

  public Vector3D[] getInertialState(Time et, String observer, String target, String abcorr) throws GeometryInformationNotAvailableException {
    Vector3D[] inertialState = new Vector3D[2];
    Object[] parameters = new Object[] {observer, target, abcorr};
    if (this.caching) { // check whether we have cached values
      Object returnVal = getSpiceInfoValue(et, "InertialState", parameters);
      if (returnVal != null) { // if we actually got a match for the desired inertial state object, then return that, else go to spice call
        return (Vector3D[]) returnVal;
      }
    }
    try {
      Rotation J2000ToFrame = new Rotation(CSPICE.pxform("J2000", getStringFromBodiesMap(target, Body::getNAIFBodyFrame), et.toET()), 1);
      Vector3D[] stateVector = getState(et, observer, target, abcorr);
      Vector3D inertialSpacecraftPosition = J2000ToFrame.applyTo(stateVector[0]);
      Vector3D inertialSpacecraftVelocity = J2000ToFrame.applyTo(stateVector[1]);
      inertialState[0] = inertialSpacecraftPosition;
      inertialState[1] = inertialSpacecraftVelocity;
      if (this.caching) {
        setSpiceInfoValue(et, "InertialState", parameters, inertialState);
      }
    } catch (SpiceErrorException e) {
      throw new GeometryInformationNotAvailableException(e.getMessage());
    }
    return inertialState;
  }

  private double getDoubleFromBodiesMap(String body, Function<Body, Double> getter) throws GeometryInformationNotAvailableException {
    if(bodiesMap.containsKey(body)){
      return getter.apply(bodiesMap.get(body));
    }
    else{
      throw new GeometryInformationNotAvailableException("Required information not loaded for body: " + body + "\nThis can be loaded by passing the relevant information into the setBodies method of GenericGeometryResources");
    }
  }

  private String getStringFromBodiesMap(String body, Function<Body, String> getter) throws GeometryInformationNotAvailableException {
    if(bodiesMap.containsKey(body)){
      return getter.apply(bodiesMap.get(body));
    }
    else{
      throw new GeometryInformationNotAvailableException("Required information not loaded for body: " + body + "\nThis can be loaded by passing the relevant information into the setBodies method of GenericGeometryResources");
    }
  }

  private int getIntFromBodiesMap(String body, Function<Body, Integer> getter) throws GeometryInformationNotAvailableException {
    if(bodiesMap.containsKey(body)){
      return getter.apply(bodiesMap.get(body));
    }
    else{
      throw new GeometryInformationNotAvailableException("Required information not loaded for body: " + body + "\nThis can be loaded by passing the relevant information into the setBodies method of GenericGeometryResources");
    }
  }
}
