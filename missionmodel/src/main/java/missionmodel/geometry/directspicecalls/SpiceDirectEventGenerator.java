package missionmodel.geometry.directspicecalls;

import missionmodel.geometry.spiceinterpolation.Body;
import missionmodel.geometry.interfaces.GeometryInformationNotAvailableException;
import missionmodel.geometry.interfaces.FunctionWithGeometricException;
import missionmodel.geometry.interfaces.GeometricEventGenerator;

import missionmodel.Window;
import missionmodel.geometry.returnedobjects.IlluminationAngles;
import spice.basic.CSPICE;
import gov.nasa.jpl.time.Duration;
import gov.nasa.jpl.time.Time;
import spice.basic.SpiceErrorException;

import java.util.*;
import java.util.function.Function;

public class SpiceDirectEventGenerator implements GeometricEventGenerator {

  private Map<String, Body> bodiesMap;
  private SpiceDirectTimeDependentStateCalculator stateCalculator;

  public SpiceDirectEventGenerator() {
    this.stateCalculator = new SpiceDirectTimeDependentStateCalculator(false);
    this.bodiesMap = this.stateCalculator.getBodiesMap();
  }

  //<editor-fold desc="Methods to fulfill interface">
  @Override
  public List<Window> getOccultations(Time start, Time endTime, Duration stepSize, String observer, String target, String occultingBody, String abcorr, boolean mergePartials, boolean isTargetAPoint, boolean useDSK) throws GeometryInformationNotAvailableException {
    Body occultingObject = this.bodiesMap.get(occultingBody);
    Body targetObject = this.bodiesMap.get(target);
    String targetFrame = isTargetAPoint ? "   " : targetObject.getNAIFBodyFrame();
    String targetType  = isTargetAPoint ? "POINT" : "ellipsoid";
    List<Window> fullEclipseList = new ArrayList<>();

    if(useDSK) {
      // with DSK, you're only allowed to pick a point backing object, which means you can only find full eclipses
      try {
        fullEclipseList.addAll(geometryFinderOccultations("ANY", occultingObject.getName(), "DSK/UNPRIORITIZED", occultingObject.getNAIFBodyFrame(),
          target, "POINT", targetFrame, abcorr,
          observer, stepSize, start, endTime));
      } catch (SpiceErrorException e) {
        throw new GeometryInformationNotAvailableException(e.getMessage());
      }
    }
    else if(mergePartials){
      try {
        fullEclipseList.addAll(geometryFinderOccultations("ANY", occultingObject.getName(), "ellipsoid", occultingObject.getNAIFBodyFrame(),
          target, targetType, targetFrame, abcorr,
          observer, stepSize, start, endTime));
      } catch (SpiceErrorException e) {
        throw new GeometryInformationNotAvailableException(e.getMessage());
      }
    }
    else{
      // first we need to get the lists of full, annular, and partial eclipses from SPICE
      try {
        fullEclipseList.addAll(geometryFinderOccultations("FULL", occultingObject.getName(), "ellipsoid", occultingObject.getNAIFBodyFrame(),
          target, targetType, targetFrame, abcorr,
          observer, stepSize, start, endTime));
      } catch (SpiceErrorException e) {
        throw new GeometryInformationNotAvailableException(e.getMessage());
      }

      try {
        fullEclipseList.addAll(geometryFinderOccultations("ANNULAR", occultingObject.getName(), "ellipsoid", occultingObject.getNAIFBodyFrame(),
          target, targetType, targetFrame, abcorr,
          observer, stepSize, start, endTime));
      } catch (SpiceErrorException e) {
        throw new GeometryInformationNotAvailableException(e.getMessage());
      }

      try {
        fullEclipseList.addAll(geometryFinderOccultations("PARTIAL", occultingObject.getName(), "ellipsoid", occultingObject.getNAIFBodyFrame(),
          target, targetType, targetFrame, abcorr,
          observer, stepSize, start, endTime));
      } catch (SpiceErrorException e) {
        throw new GeometryInformationNotAvailableException(e.getMessage());
      }

      Collections.sort(fullEclipseList);
      int numEclipsesBeforeProcessing = fullEclipseList.size();

      // now that we've sorted the eclipse windows, we have to process them to deal with eclipse types
      // transitioning to each other, and use smaller step sizes to grab partial eclipses we missed before around full eclipses
      for (int i = 0; i < numEclipsesBeforeProcessing; i++) {
        if (fullEclipseList.get(i).getType().equals("FULL")) {
          // check that this full eclipse has a partial eclipse entry
          if (i == 0 ||
            !(fullEclipseList.get(i - 1).getType().equals("PARTIAL") &&
              fullEclipseList.get(i).getStart().absoluteDifference(fullEclipseList.get(i - 1).getEnd()).lessThan(new Duration("00:00:01")))) {
            try {
              List<Window> foundPartialEclipses = geometryFinderOccultations("PARTIAL", occultingObject.getName(), "ellipsoid", occultingObject.getNAIFBodyFrame(),
                target, targetType, targetFrame, abcorr,
                observer, new Duration("00:00:00.5"), fullEclipseList.get(i).getStart().subtract(new Duration("00:05:00")), fullEclipseList.get(i).getStart().add(new Duration("00:00:01")));
              if (foundPartialEclipses.isEmpty()) {
                //we add a fake partial eclipse for a tenth of a second because there must have been something
                fullEclipseList.add(new Window(fullEclipseList.get(i).getStart().subtract(new Duration("00:00:01")), fullEclipseList.get(i).getStart(), "PARTIAL"));
              } else {
                // we add the real partial eclipse that SPICE found
                fullEclipseList.add(foundPartialEclipses.get(0));
              }
            } catch (SpiceErrorException e) {
              throw new GeometryInformationNotAvailableException(e.getMessage());
            }

          }

          // check that this full eclipse has a partial eclipse exit
          if (i == numEclipsesBeforeProcessing - 1 ||
            !(fullEclipseList.get(i + 1).getType().equals("PARTIAL") &&
              fullEclipseList.get(i).getEnd().absoluteDifference(fullEclipseList.get(i + 1).getStart()).lessThan(new Duration("00:00:01")))) {
            try {
              List<Window> foundPartialEclipses = geometryFinderOccultations("PARTIAL", occultingObject.getName(), "ellipsoid", occultingObject.getNAIFBodyFrame(),
                target, targetType, targetFrame, abcorr,
                observer, new Duration("00:00:00.5"), fullEclipseList.get(i).getEnd().subtract(new Duration("00:00:01")), fullEclipseList.get(i).getEnd().add(new Duration("00:05:00")));
              if (foundPartialEclipses.isEmpty()) {
                //we add a fake partial eclipse for a tenth of a second because there must have been something
                fullEclipseList.add(new Window(fullEclipseList.get(i).getEnd(), fullEclipseList.get(i).getEnd().add(new Duration("00:00:01")), "PARTIAL"));
              } else {
                // we add the real partial eclipse that SPICE found
                fullEclipseList.add(foundPartialEclipses.get(0));
              }
            } catch (SpiceErrorException e) {
              throw new GeometryInformationNotAvailableException(e.getMessage());
            }
          }
        }
      }

      // we re-sort here since we tacked stuff on, no matter when it happened, to the end in the above code
      Collections.sort(fullEclipseList);
    }

    return fullEclipseList;

  }

  @Override
  public List<Time> getPeriapses(Time start, Time endTime, Duration stepSize, String observer, String target, double maxDistanceFilter, String abcorr) throws GeometryInformationNotAvailableException {
    Body targetObject = this.bodiesMap.get(target);
    if (targetObject == null) { // if target body is not in bodies map, return empty array list
      return new ArrayList<>();
    }
    List<Time> periapsisList = new ArrayList<>();
    try {
      // get each window of times that match the condition where the distance from the observer (spacecraft) to target (object) is a minimum
      List<Window> periapsisWindows = geometryFinderDistance(targetObject.getName(), abcorr, observer, "LOCMIN", 0.0, 0.0, stepSize, start, endTime);
      for (Window periapsisWindow : periapsisWindows) {
        // if the body at the periapsis point is closer to the target body than the max distance filter, add the time
        if (this.stateCalculator.getRange(periapsisWindow.getStart(), observer, target, abcorr) <= maxDistanceFilter) {
          periapsisList.add(periapsisWindow.getStart());
        }
      }
    } catch (SpiceErrorException e) {
      throw new GeometryInformationNotAvailableException(e.getMessage());
    }
    return periapsisList;
  }

  @Override
  public List<Time> getApoapses(Time start, Time endTime, Duration stepSize, String observer, String target, double minDistanceFilter, String abcorr) throws GeometryInformationNotAvailableException {
    Body targetObject = this.bodiesMap.get(target);
    if (targetObject == null) { // if target body is not in bodies map, return empty array list
      return new ArrayList<>();
    }
    List<Time> apoapsisList = new ArrayList<>();
    try {
      // get each window of times that match the condition where the distance from the observer (spacecraft) to target (object) is a maximum
      List<Window> apoapsisWindows = geometryFinderDistance(targetObject.getName(), abcorr, observer, "LOCMAX", 0.0, 0.0, stepSize, start, endTime);
      for (Window apoapsisWindow : apoapsisWindows) {
        // if the body at the apoapsis point is farther from the target body than the min distance filter, add the time
        if (this.stateCalculator.getRange(apoapsisWindow.getStart(), observer, target, abcorr) >= minDistanceFilter) {
          apoapsisList.add(apoapsisWindow.getStart());
        }
      }
    } catch (SpiceErrorException e) {
      throw new GeometryInformationNotAvailableException(e.getMessage());
    }
    return apoapsisList;
  }

  @Override
  public List<Window> getConjunctions(Time start, Time endTime, Duration stepSize, String observer, String target, String conjunctingBody, String abcorr, double maxConjunctionAngle) throws GeometryInformationNotAvailableException {
    List<Window> fullConjunctionList = new ArrayList<>();
    String conjunctingBodyFrame = this.bodiesMap.containsKey(conjunctingBody) ? this.bodiesMap.get(conjunctingBody).getNAIFBodyFrame() : "NULL";
    String targetBodyFrame      = this.bodiesMap.containsKey(target) ? this.bodiesMap.get(target).getNAIFBodyFrame() : "NULL";
    double[] cnfine = new double[]{start.toET(), endTime.toET()}; // define search window in a format gfsep can take
    int nintvls = 2 + (int)Math.ceil(endTime.subtract(start).totalSeconds() / stepSize.totalSeconds());
    try {
      double[] angleResults = CSPICE.gfsep(conjunctingBody, "POINT", conjunctingBodyFrame, target, "POINT", targetBodyFrame, abcorr, observer, "<", maxConjunctionAngle * (Math.PI/180), 0.0, stepSize.totalSeconds(), nintvls, cnfine);
      for(int i = 0; i < angleResults.length; i += 2) { // result is in the format of: start1, end1, start2, end2, ....
        fullConjunctionList.add(new Window(Time.fromET(angleResults[i]), Time.fromET(angleResults[i + 1])));
      }
      return fullConjunctionList;
    } catch (SpiceErrorException e) {
      throw new GeometryInformationNotAvailableException(e.getMessage());
    }
  }

  @Override
  public List<Window> getTerminatorCrossings(Time start, Time endTime, Duration stepSize, Duration refinementTime, String observer, String target, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException {
    List<Window> terminatorCrossings = new ArrayList<>();
    Body targetObject = this.bodiesMap.get(target);
    if (targetObject == null) { // if target body is not in bodies map, return empty array list
      throw new GeometryInformationNotAvailableException("Body " + targetObject + " not defined with SPICE frame in input data");
    }
    double prevSolarIncidenceAngle = 0.0;
    Time prevTime = null;
    double currentSolarIncidenceAngle;
    Time terminatorEntry = null; // keep track of when we entered a terminator (shadow to sunlit), so we can make a window from entry to exit
    for(Time currTime = start; currTime.lessThanOrEqualTo(endTime); currTime = currTime.add(stepSize)){
      IlluminationAngles prevIlluminationAngles = this.stateCalculator.getIlluminationAngles(currTime, observer, target, abcorr, useDSK);
      if (prevIlluminationAngles == null) {
        continue;
      }
      currentSolarIncidenceAngle = prevIlluminationAngles.getIncidenceAngle();
      if (currTime.equals(start)) {
        prevTime = currTime;
        if(currentSolarIncidenceAngle < 90) {
          terminatorEntry = start;
        }
      } else {
        // if there was no change in the solar incidence angle condition from previous time to this time, move onto next time instant
        if ((prevSolarIncidenceAngle < 90 && currentSolarIncidenceAngle < 90) || (prevSolarIncidenceAngle >= 90 && currentSolarIncidenceAngle >= 90)) {
          prevTime = currTime;
        }
        // if there was a change, search within prev time to current time window more refined to get when the exact crossing happened
        else {
          if (prevSolarIncidenceAngle >= 90 && currentSolarIncidenceAngle < 90) { // condition from shadow to sunlit (entry)
            Time moreRefinedTime = refinedTerminatorCrossing(prevTime, currTime, refinementTime, observer, target, abcorr, useDSK, (x) -> x < 90);
            terminatorEntry = moreRefinedTime;
            prevTime = moreRefinedTime;
          } else if (prevSolarIncidenceAngle < 90 && currentSolarIncidenceAngle >= 90) { // condition from sunlit to shadow (exit)
            Time moreRefinedTime = refinedTerminatorCrossing(prevTime, currTime, refinementTime, observer, target, abcorr, useDSK, (x) -> x >= 90);
            terminatorCrossings.add(new Window(terminatorEntry, moreRefinedTime));
            terminatorEntry = null;
            prevTime = moreRefinedTime;
          }
        }
      }
      prevSolarIncidenceAngle = currentSolarIncidenceAngle;
    }
    if (terminatorEntry != null) { // if there was a terminator entry event that did not pair up with a terminator exit
      terminatorCrossings.add(new Window(terminatorEntry, endTime)); // endTime must be in middle of terminator event, so use that as boundary end
    }
    return terminatorCrossings;
  }

  @Override
  public List<Window> getOrbitNumbers(Time start, Time endTime, Duration stepSize, String observer, String target, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException {
    throw new GeometryInformationNotAvailableException("Each mission has a different definition of orbit boundaries - override this method to define a mission-specific calculation");
  }
  //</editor-fold>

  //<editor-fold desc="Helper methods for interface-fulfilling methods, or other useful public utility methods">
  public List<Window> getWindowsWhenConditionMet(Time start, Time endTime, Duration stepSize, Duration refinementTime, FunctionWithGeometricException<Object[], Object> geometricFunction, Function<Object, Boolean> conditionFunction, Object... otherParameters) throws GeometryInformationNotAvailableException {
    List<Window> eventWindow = new ArrayList<>();
    Object[] parameterList = new Object[otherParameters.length + 1];
    for (int i=0; i<otherParameters.length; i++) {
      parameterList[i+1] = otherParameters[i];
    }
    Time entryTime = null;
    Time exitTime = null;
    for(Time currTime = start; currTime.lessThanOrEqualTo(endTime); currTime = currTime.add(stepSize)){
      parameterList[0] = currTime;
      Object output = geometricFunction.apply(parameterList);
      if (conditionFunction.apply(output)) {
        if (entryTime == null) { // if we have not already encountered a valid match in this window
          entryTime = getRefinedTime(geometricFunction, conditionFunction, currTime.subtract(stepSize), currTime, refinementTime, parameterList, "first");
          exitTime = null; // signify we have started a new window and must look for next exit mismatch
        }
      } else {
        if (exitTime == null && entryTime != null) { // if we have not already encountered a valid mismatch in this window
          exitTime = getRefinedTime(geometricFunction, conditionFunction, currTime.subtract(stepSize), currTime, refinementTime, parameterList, "last");
          eventWindow.add(new Window(entryTime, exitTime));
          entryTime = null; // signify we have ended a window and must look for next entry match
        }
      }
    }
    return eventWindow;
  }

  public Time refinedTerminatorCrossing(Time start, Time endTime, Duration resolution, String observer, String target, String abcorr, boolean useDSK, Function<Double, Boolean> conditionFunction) throws GeometryInformationNotAvailableException {
    if (endTime.subtract(start).lessThan(resolution)) { // if the gap between start and end time is less than resolution, no need to search anymore...End time should be the time where the condition function is met by design
      return endTime;
    }
    Time middleTime = new Window(start, endTime).getMidpoint();
    IlluminationAngles prevIlluminationAngles = this.stateCalculator.getIlluminationAngles(middleTime, observer, target, abcorr, useDSK);
    double currentSolarIncidenceAngle = prevIlluminationAngles.getIncidenceAngle();
    if (conditionFunction.apply(currentSolarIncidenceAngle)) { // if the condition function applies, search from start to middle (Since end time should match condition function as well)
      return refinedTerminatorCrossing(start, middleTime, resolution, observer, target, abcorr, useDSK, conditionFunction);
    } else { // if condition function applies, search from middle to end (Since start time should not match condition function as well)
      return refinedTerminatorCrossing(middleTime, endTime, resolution, observer, target, abcorr, useDSK, conditionFunction);
    }
  }

  public Time getRefinedTime(FunctionWithGeometricException<Object[], Object> geometricFunction, Function<Object, Boolean> conditionFunction, Time searchStart, Time searchEnd, Duration refinementTime, Object[] parameterList, String searchType) throws GeometryInformationNotAvailableException{
    if (searchEnd.subtract(searchStart).lessThanOrEqualTo(refinementTime)) {
      if (searchType.equals("last")) { // if we are trying to find the latest match, then the start of the search will for sure be valid
        return searchStart;
      } else { // if we are trying to find the earliest match, then the end of the search will for sure be valid
        return searchEnd;
      }
    }
    Time middleTime = new Window(searchStart, searchEnd).getMidpoint();
    parameterList[0] = middleTime; // add it to the parameter list to get the value of the function at that time
    Object output = geometricFunction.apply(parameterList);
    if (conditionFunction.apply(output)) {
      if (searchType.equals("last")) { // if the condition matches, and we are looking for the latest match, then search between the end of search and this match
        return getRefinedTime(geometricFunction, conditionFunction, middleTime, searchEnd, refinementTime, parameterList, "last");
      } else { // if the condition matches, and we are looking for the earliest match, then search between the start of the search and this match
        return getRefinedTime(geometricFunction, conditionFunction, searchStart, middleTime, refinementTime, parameterList, "first");
      }
    } else {
      if (searchType.equals("last")) { // if the condition does not match, and we are looking for the latest match, that means we went past it, so search between start and this non match
        return getRefinedTime(geometricFunction, conditionFunction, searchStart, middleTime, refinementTime, parameterList, "last");
      } else { // if the condition does not match, and we are looking for the earliest match, that means we went past it, so search between this non match and the end
        return getRefinedTime(geometricFunction, conditionFunction, middleTime, searchEnd, refinementTime, parameterList, "first");
      }
    }
  }
  //</editor-fold>

  //<editor-fold desc="Lower-level geometry finder access functions">
  public static List<Window> geometryFinderDistance(String targetBody, String abcorr, String observer, String relationalOperator, double referenceValue, double adjust, Duration stepSize, Time startSearch, Time endSearch) throws SpiceErrorException {
    int nintvls = 2 + (int)Math.ceil(endSearch.subtract(startSearch).totalSeconds() / stepSize.totalSeconds());
    double[] cnfine = new double[]{startSearch.toET(), endSearch.toET()};
    double[] distanceResults = CSPICE.gfdist(targetBody, abcorr, observer, relationalOperator, referenceValue, adjust, stepSize.totalSeconds(), nintvls, cnfine);
    List<Window> toReturn = new ArrayList<>();

    for(int i = 0; i < distanceResults.length; i += 2) {
      toReturn.add(new Window(Time.fromET(distanceResults[i]), Time.fromET(distanceResults[i + 1])));
    }

    return toReturn;
  }

  public static List<Window> geometryFinderOccultations(String occultationType, String occultingBody, String frontBodyShape, String frontFrame,
                                                        String targetBody, String backBodyShape, String backBodyFrame, String abcorr,
                                                        String observer, Duration stepSize, Time startSearch, Time endSearch) throws SpiceErrorException {

    int nintvls = 2 + (int)Math.ceil((endSearch.subtract(startSearch).totalSeconds())/stepSize.totalSeconds());
    double[] cnfine = new double[]{startSearch.toET(), endSearch.toET()};

    double[] occultationResults = CSPICE.gfoclt(occultationType, occultingBody, frontBodyShape, frontFrame, targetBody,
      backBodyShape, backBodyFrame, abcorr, observer, stepSize.totalSeconds(), nintvls, cnfine);

    List<Window> toReturn = new ArrayList<>();
    for(int i = 0; i < occultationResults.length; i+=2){
      toReturn.add(new Window(Time.fromET(occultationResults[i]), Time.fromET(occultationResults[i+1]), occultationType));
    }
    return toReturn;
  }


  public static List<Window> geometryFinderPositionVector(String targetBody, String frame, String abcorr, String observer,
                                                          String coordinateSystem, String coordinateOfInterest, String relationalOperator,
                                                          double referenceValue, double adjust, Duration stepSize,
                                                          Time startSearch, Time endSearch) throws SpiceErrorException {

    int nintvls = 2 + (int)Math.ceil((endSearch.subtract(startSearch).totalSeconds())/stepSize.totalSeconds());
    double[] cnfine = new double[]{startSearch.toET(), endSearch.toET()};

    double[] windowResults = CSPICE.gfposc(targetBody, frame, abcorr, observer, coordinateSystem, coordinateOfInterest,
      relationalOperator, referenceValue, adjust, stepSize.totalSeconds(), nintvls, cnfine);

    List<Window> toReturn = new ArrayList<>();
    for(int i = 0; i < windowResults.length; i+=2){
      toReturn.add(new Window(Time.fromET(windowResults[i]), Time.fromET(windowResults[i+1])));
    }
    return toReturn;

  }
  //</editor-fold>
}
