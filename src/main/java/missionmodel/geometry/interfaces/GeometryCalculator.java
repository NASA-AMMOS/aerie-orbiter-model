package missionmodel.geometry.interfaces;

import missionmodel.geometry.spiceinterpolation.Body;

import java.util.Map;

/***
 * This interface exists to pass calculators into SpiceResourcePopulater and then BodyGeometryGenerator - the idea is we have a generic one provided
 * in the MM repo (GenericGeometryCalculator) but missions can extend it with their own mission-specific calculations
 * they want done on certain bodies while everything else is being calculated, without having to reinvent all the machinery
 * that goes and figures out what times to calculate the quantities at
 */
public interface GeometryCalculator {

  /**
   * To be called before a model pass - gives the calculator a map of bodies so it can look up their properties while calculating if it needs to
   * @param bodies
   */
  void setBodies(Map<String, Body> bodies);

  /**
   * To be called during a model pass - implement this to do any calculations you want to do whenever this is called
   * @param body the body which you want to generate information for at the current time
   * @throws GeometryInformationNotAvailableException
   */
  void calculateGeometry(Body body) throws GeometryInformationNotAvailableException;
}
