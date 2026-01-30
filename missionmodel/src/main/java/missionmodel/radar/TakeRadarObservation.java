package missionmodel.radar;

import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.Polynomial;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export.WithDefaults;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import missionmodel.Mission;
import missionmodel.data.Data;
import missionmodel.power.pel.Radar_State;

import static gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource.set;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.polynomial.Polynomial.polynomial;
import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;

@ActivityType("TakeRadarObservation")
public class TakeRadarObservation {

  @Export.Parameter
  public int schedulingPriority;
  @Export.Parameter
  public boolean scheduled;
  @Export.Parameter
  public Duration duration;
  @Export.Parameter
  public RadarDataCollectionMode mode = RadarDataCollectionMode.LOW_RES;
  @Export.Parameter
  public int bin;

  public TakeRadarObservation() {
  }

  public TakeRadarObservation(RadarDataCollectionMode radarDataCollectionMode) {
    this.mode = radarDataCollectionMode;
  }

  public TakeRadarObservation(int schedulingPriority, boolean scheduled, Duration duration,
      RadarDataCollectionMode mode, int bin) {
    this.schedulingPriority = schedulingPriority;
    this.scheduled = scheduled;
    this.duration = duration;
    this.mode = mode;
    this.bin = bin;
  }

  // Getters required by annotation processor
  public int schedulingPriority() {
    return schedulingPriority;
  }

  public boolean scheduled() {
    return scheduled;
  }

  public Duration duration() {
    return duration;
  }

  public RadarDataCollectionMode mode() {
    return mode;
  }

  public int bin() {
    return bin;
  }

  @ActivityType.EffectModel
  public void run(Mission model) {

    // Do nothing if not scheduled yet
    if (!scheduled) {
      return;
    }

    // Turn radar on
    DiscreteEffects.set(model.pel.radarState, Radar_State.ON);

    double newRate = mode.getDataRate();
    // Spawn an activity to playback
    // spawn(model, new ChangeDataGenerationRate(0, newRate*1e6)); // Conversion
    // from Mbps -> bps
    // @todo Temporary injection of Data Model Code - remove
    Data data = model.getData();
    var binToChange = data.getOnboardBin(bin);

    if (newRate > 0) {
      set((MutableResource<Polynomial>) binToChange.desiredReceiveRate, polynomial(newRate * 1e6));
      set((MutableResource<Polynomial>) binToChange.desiredRemoveRate, polynomial(0));
    } else {
      set((MutableResource<Polynomial>) binToChange.desiredReceiveRate, polynomial(0));
      set((MutableResource<Polynomial>) binToChange.desiredRemoveRate, polynomial(-newRate * 1e6));
    }
    // @todo End of Temporary code

    DiscreteEffects.set(model.radarModel.RadarDataMode, mode);

    // Set radar power state based on mode
    if (mode == RadarDataCollectionMode.OFF) {
      DiscreteEffects.set(model.pel.radarState, Radar_State.OFF); // 0W
    } else if (mode == RadarDataCollectionMode.HI_RES) {
      DiscreteEffects.set(model.pel.radarState, Radar_State.ON_HI); // 543.2W
    } else if (mode == RadarDataCollectionMode.MED_RES) {
      DiscreteEffects.set(model.pel.radarState, Radar_State.ON_MED); // 200w
    } else if (mode == RadarDataCollectionMode.LOW_RES) {
      DiscreteEffects.set(model.pel.radarState, Radar_State.ON_LOW); // 50w
    }

    // Turn radar off after duration
    delay(duration);
    DiscreteEffects.set(model.pel.radarState, Radar_State.OFF);

    // Stop collecting radar data
    DiscreteEffects.set(model.radarModel.RadarDataMode, RadarDataCollectionMode.OFF);

    // Configure the bin receive rate
    set((MutableResource<Polynomial>) binToChange.desiredReceiveRate, polynomial(0));
    set((MutableResource<Polynomial>) binToChange.desiredRemoveRate, polynomial(0));
  }

  public static @WithDefaults final class Defaults {
    public static int schedulingPriority = 1;
    public static boolean scheduled = true;
    public static Duration duration = Duration.of(1, Duration.MINUTES);
    public static int bin = 0;
  }
}
