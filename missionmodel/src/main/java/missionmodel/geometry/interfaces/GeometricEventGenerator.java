package missionmodel.geometry.interfaces;

import missionmodel.Window;
import gov.nasa.jpl.time.Duration;
import gov.nasa.jpl.time.Time;

import java.util.List;

public interface GeometricEventGenerator {

  /**
   * @param start The start time of the search for events
   * @param endTime The end time of the search for events
   * @param stepSize The step size the event searcher should use to look for them - must make smaller than the smallest event you want to capture, but output precision is not limited to this
   * @param observer The entity that is observing the occultation or eclipse
   * @param target The body that is occluded
   * @param occultingBody The body that is in front of 'target' blocking the view of it
   * @param abcorr Aberration correction that should be applied when calculating event
   * @param mergePartials If true, returns one window for an attached partial->full->partial series of eclipses. If false, returns three adjacent windows. Not necessarily supported by all implementations
   * @param isTargetAPoint Only usable if useDSK is false and mergePartials is true. Searches for a target point instead of target ellipsoid
   * @param useDSK If a DSK should be used for the 'occultingBody'
   * @return List of window objects, one for each occultation returned from the search
   * @throws GeometryInformationNotAvailableException
   */
  List<Window> getOccultations(           Time start, Time endTime, Duration stepSize, String observer, String target, String occultingBody,  String abcorr, boolean mergePartials, boolean isTargetAPoint, boolean useDSK) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param start The start time of the search for events
   * @param endTime The end time of the search for events
   * @param stepSize The step size the event searcher should use to look for them - must make smaller than the smallest event you want to capture, but output precision is not limited to this
   * @param observer The entity that is orbiting the center body
   * @param target The center body
   * @param maxDistanceFilter Events that occur higher than this filter (km) are not included in the returned list
   * @param abcorr Aberration correction that should be applied when calculating event
   * @return List of Time objects, one at each qualifying periapsis
   * @throws GeometryInformationNotAvailableException
   */
  List<Time>   getPeriapses(              Time start, Time endTime, Duration stepSize, String observer, String target, double maxDistanceFilter, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param start The start time of the search for events
   * @param endTime The end time of the search for events
   * @param stepSize The step size the event searcher should use to look for them - must make smaller than the smallest event you want to capture, but output precision is not limited to this
   * @param observer The entity that is orbiting the center body
   * @param target The center body
   * @param minDistanceFilter Events that occur lower than this filter (km) are not included in the returned list
   * @param abcorr Aberration correction that should be applied when calculating event
   * @return List of Time objects, one at each qualifying apoapsis
   * @throws GeometryInformationNotAvailableException
   */
  List<Time>   getApoapses(               Time start, Time endTime, Duration stepSize, String observer, String target, double minDistanceFilter, String abcorr) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param start The start time of the search for events
   * @param endTime The end time of the search for events
   * @param stepSize The step size the event searcher should use to look for them - must make smaller than the smallest event you want to capture, but output precision is not limited to this
   * @param observer The entity that lies at the vertex of the angle in question - for example, "EARTH" for SEP angle
   * @param target One of the two target bodies - event times are when this is near conjunctingBody as seen by observer
   * @param conjunctingBody One of the two target bodies - event times are when this is near target as seen by observer
   * @param abcorr Aberration correction that should be applied when calculating event
   * @param maxConjunctionAngle Time periods count as in conjunction if they have an angle lower than this
   * @return List of window objects, one for each conjunction returned from the search
   * @throws GeometryInformationNotAvailableException
   */
  List<Window> getConjunctions(           Time start, Time endTime, Duration stepSize, String observer, String target, String conjunctingBody, String abcorr, double maxConjunctionAngle) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param start The start time of the search for events
   * @param endTime The end time of the search for events
   * @param stepSize The step size the event searcher should use to look for them - must make smaller than the smallest event you want to capture, but output precision is not limited to this
   * @param refinementTime The output precision for the Times in the returned windows
   * @param observer The entity whose subpoint crosses back and forth across terminators on the 'target' body
   * @param target The body that the solar terminator line is falling on
   * @param abcorr Aberration correction that should be applied when calculating event
   * @param useDSK If a DSK should be used for the 'target'
   * @return List of window objects, each of which runs from a dark-to-lit terminator to a lit-to-dark terminator
   * @throws GeometryInformationNotAvailableException
   */
  List<Window> getTerminatorCrossings(    Time start, Time endTime, Duration stepSize, Duration refinementTime, String observer, String target, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException;

  /**
   *
   * @param start The start time of the search for events
   * @param endTime The end time of the search for events
   * @param stepSize The step size the event searcher should use to look for them - must make smaller than the smallest event you want to capture, but output precision is not limited to this
   * @param observer The entity that is orbiting the center body
   * @param target The center body
   * @param abcorr Aberration correction that should be applied when calculating event
   * @param useDSK If a DSK should be used for the 'target'
   * @return List of window objects, each of which starts at the orbit whose index it is in the return array and ending at the next orbit boundary
   * @throws GeometryInformationNotAvailableException
   */
  List<Window> getOrbitNumbers(           Time start, Time endTime, Duration stepSize, String observer, String target, String abcorr, boolean useDSK) throws GeometryInformationNotAvailableException;
}
