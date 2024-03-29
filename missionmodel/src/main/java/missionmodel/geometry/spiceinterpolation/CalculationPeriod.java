package missionmodel.geometry.spiceinterpolation;

import gov.nasa.jpl.time.Duration;
import gov.nasa.jpl.time.Time;

public class CalculationPeriod {
  private Time start;
  private Time end;
  private Duration minTimeStep;
  private Duration maxTimeStep;
  private double threshold;

  public CalculationPeriod(Time start, Time end, Duration minTimeStep, Duration maxTimeStep, double threshold){
    this.start = start;
    this.end = end;
    this.minTimeStep = minTimeStep;
    this.maxTimeStep = maxTimeStep;
    this.threshold = threshold;
  }

  public Time getStart() {
    return start;
  }

  public Time getEnd() {
    return end;
  }

  public Duration getMinTimeStep() {
    return minTimeStep;
  }

  public Duration getMaxTimeStep() {
    return maxTimeStep;
  }

  public double getThreshold() {
    return threshold;
  }

  public Duration getDuration(){
    return end.subtract(start);
  }

}
