package missionmodel.geometry.spiceinterpolation;

//import gov.nasa.jpl.engine.ModelingEngine;
import gov.nasa.jpl.time.Duration;
import gov.nasa.jpl.time.Time;
import missionmodel.AbsoluteClock;
import missionmodel.JPLTimeConvertUtility;
import missionmodel.geometry.resources.GenericGeometryResources;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.util.*;

import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.currentValue;

public class VariableTimeStepGenerator {

  private Map<String, Time> nextTimeToCalculateBody;
  private Map<String, ArrayDeque<Map.Entry<Time,Vector3D>>> previousCalculatedValuesPerBody;
  private double eps;
  private Duration minStep;
  private Duration maxStep;

  private AbsoluteClock absoluteClock;

  private GenericGeometryResources geomRes;

  public VariableTimeStepGenerator(AbsoluteClock absoluteClock, GenericGeometryResources geometryResource, List<String> bodyNames, Double eps, Duration minStep, Duration maxStep, Time startTime){
    this.absoluteClock = absoluteClock;
    this.geomRes = geometryResource;
    this.eps = eps;
    this.minStep = minStep;
    this.maxStep = maxStep;
    nextTimeToCalculateBody = new HashMap<>();
    previousCalculatedValuesPerBody = new HashMap<>();
    for(String bodyName : bodyNames){
      nextTimeToCalculateBody.put(bodyName, startTime);
      previousCalculatedValuesPerBody.put(bodyName, new ArrayDeque<>());
    }
  }

  public Map.Entry<String, Time> nextTimeToJumpToAndItsBody(String priorBody){
    updateInternalHistory(priorBody);
    Time nextTime = null;
    String nextBody = null;
    for(Map.Entry<String, Time> potentialBodyAndTime : nextTimeToCalculateBody.entrySet()){
      if(nextTime == null || nextTime.greaterThan(potentialBodyAndTime.getValue())){
        nextTime = potentialBodyAndTime.getValue();
        nextBody = potentialBodyAndTime.getKey();
      }
    }

    return new AbstractMap.SimpleEntry<>(nextBody, nextTime);
  }

  private void updateInternalHistory(String bodyName){
    // remove 4 calculations ago from history and add newest calculation
    previousCalculatedValuesPerBody.get(bodyName).push(new AbstractMap.SimpleEntry<>(
      JPLTimeConvertUtility.nowJplTime(absoluteClock),
      currentValue(geomRes.BODY_POS_ICRF.get(bodyName))));

    Duration timeStep;

    if(previousCalculatedValuesPerBody.get(bodyName).size() > 3) {
      previousCalculatedValuesPerBody.get(bodyName).removeLast();

      List<Map.Entry<Time, Vector3D>> priorValues = new ArrayList<>();
      priorValues.addAll(previousCalculatedValuesPerBody.get(bodyName));

      // now we need to calculate the min timestep based on each component of the vector position
      timeStep = maxStep;

      Duration proposedTimeStep = nextStepSize(priorValues.get(2).getKey(), priorValues.get(2).getValue().getX(),
        priorValues.get(1).getKey(), priorValues.get(1).getValue().getX(), priorValues.get(0).getKey(),
        priorValues.get(0).getValue().getX());
      if (proposedTimeStep.lessThan(timeStep)) timeStep = proposedTimeStep;

      proposedTimeStep = nextStepSize(priorValues.get(2).getKey(), priorValues.get(2).getValue().getY(),
        priorValues.get(1).getKey(), priorValues.get(1).getValue().getY(), priorValues.get(0).getKey(),
        priorValues.get(0).getValue().getY());
      if (proposedTimeStep.lessThan(timeStep)) timeStep = proposedTimeStep;

      proposedTimeStep = nextStepSize(priorValues.get(2).getKey(), priorValues.get(2).getValue().getZ(),
        priorValues.get(1).getKey(), priorValues.get(1).getValue().getZ(), priorValues.get(0).getKey(),
        priorValues.get(0).getValue().getZ());
      if (proposedTimeStep.lessThan(timeStep)) timeStep = proposedTimeStep;
    }
    else{
      // if we haven't built up enough history to calculate acceleration yet, just take the minimum timestep
      timeStep = minStep;
    }

    //pw.println(timeStep.totalSeconds() + "," + bodyName);
    nextTimeToCalculateBody.put(bodyName, JPLTimeConvertUtility.nowJplTime(absoluteClock).add(timeStep));
  }

  /**
   @brief Determine the next step size for a sampling routine given a desired error
   @param[in] t0 - time the third-to-last point was measured
   @param[in] x0 - value when third-to-last point was measured
   @param[in] t1 - time the second-to-last point was measured
   @param[in] x1 - value when second-to-last point was measured
   @param[in] t2 - time the last point was measured
   @param[in] x2 - last point was measured
   @param[in] eps - relative error desired
   @return next_step_size (s) - duration after which next value should be measured
   */
  private Duration nextStepSize(Time t0, Double x0, Time t1, Double x1, Time t2, Double x2){
    // to calculate the 'acceleration' of the data we need the last two data 'velocities'
    Double v2 = (x2-x1)/((t2.subtract(t1)).totalSeconds());
    Double v1 = (x1-x0)/((t1.subtract(t0)).totalSeconds());

    if(v1 - v2 == 0){
      return maxStep;
    }
    Duration next_step_size = new Duration("00:00:01").multiply((eps*Math.abs(x2))/Math.abs(v2-v1));

    // we can't let the step size be so large we miss things, or so small it slows to a crawl
    if(next_step_size.lessThan(minStep)){
      return minStep;
    }
    else if(next_step_size.greaterThan(maxStep)){
      return maxStep;
    }
    else{
      return next_step_size;
    }

  }
}
