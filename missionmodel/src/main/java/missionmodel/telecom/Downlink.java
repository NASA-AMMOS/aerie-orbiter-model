package missionmodel.telecom;

import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export.Validation;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import missionmodel.Mission;
import missionmodel.geometry.resources.EclipseTypes;
import missionmodel.power.pel.*;

import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.currentValue;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects.set;
import static gov.nasa.jpl.aerie.merlin.framework.ModelActions.delay;

@ActivityType("Downlink")
public class Downlink {

  @Export.Parameter
  public Duration duration = Duration.HOUR;

  @Export.Parameter
  public Double bitRate = 1000.0;

  public Downlink() {}

  public Downlink(Duration duration, double bitRate) {
    this.duration = duration;
    this.bitRate = bitRate;
  }

  @Validation("Collection rate is beyond buffer limit of 2000 kbps")
  @Validation.Subject("bitRate")
  public boolean validateBitRate() {
    return bitRate <= 2000.0;
  }

  @ActivityType.ControllableDuration(parameterName = "duration")
  @ActivityType.EffectModel
  public void run(Mission model) {
    // Get bit rate from parameter for now
    set(model.telecomModel.downlinkBitRate, bitRate);
    // Set spacecraft hardware associated with downlink
    // Check current state for VISAR
    set(model.pel.x_twtaState, X_TWTA_State.ON);
    set(model.pel.ka_twtaState, Ka_TWTA_State.ON);
    set(model.pel.ssrState, SSR_State.DOWNLINK);
    set(model.pel.idstState, IDST_State.DOWNLINK);
    set(model.pel.propState, Prop_State.DOWNLINK);
    Radar_State prevVisarState = currentValue(model.pel.radarState);
    set(model.pel.radarState, Radar_State.DOWNLINK);
    set(model.pel.radar_heatersState, Radar_Heaters_State.OFF);
    Imager_State prevVemState = currentValue(model.pel.imagerState);
    set(model.pel.imager_heatersState, Imager_Heaters_State.DOWNLINK);
    set(model.pel.heatersState, Heaters_State.DOWNLINK);
    set(model.pel.harnesslossState, HarnessLoss_State.DOWNLINK);

    delay(duration);

    set(model.telecomModel.downlinkBitRate, 0.0);
    set(model.pel.x_twtaState, X_TWTA_State.OFF);
    set(model.pel.ka_twtaState, Ka_TWTA_State.OFF);
    set(model.pel.ssrState, SSR_State.ON);
    set(model.pel.idstState, IDST_State.ON);
    set(model.pel.propState, Prop_State.OFF);

    if(prevVisarState.equals(Radar_State.OFF)) {
      set(model.pel.radarState, Radar_State.OFF);
      set(model.pel.radar_heatersState, Radar_Heaters_State.SCIENCE_SURVIVAL);
      set(model.pel.heatersState, Heaters_State.SURVIVAL);
      set(model.pel.harnesslossState, HarnessLoss_State.RADAR_OFF);
    } else {
      set(model.pel.radarState, Radar_State.ON);
      set(model.pel.radar_heatersState, Radar_Heaters_State.OFF);
      set(model.pel.heatersState, Heaters_State.RADAR_ON);
      set(model.pel.harnesslossState, HarnessLoss_State.RADAR_ON);
    }

    if(prevVemState.equals(Imager_State.OFF)) {
      set(model.pel.imagerState, Imager_State.OFF);
      set(model.pel.imager_heatersState, Imager_Heaters_State.SCIENCE_SURVIVAL);
    } else {
      set(model.pel.imagerState, Imager_State.ON);
      set(model.pel.imager_heatersState, Imager_Heaters_State.IMAGER_ON);
    }

    // Send data from s/c to ground

  }

}
