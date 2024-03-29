package missionmodel.telecom;

import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
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
  public Duration duration;

  @Export.Parameter
  public Double bitRate;

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
    VISAR_State prevVisarState = currentValue(model.pel.visarState);
    set(model.pel.visarState, VISAR_State.DOWNLINK);
    set(model.pel.visar_heatersState, VISAR_Heaters_State.OFF);
    VEM_State prevVemState = currentValue(model.pel.vemState);
    set(model.pel.vem_heatersState, VEM_Heaters_State.DOWNLINK);
    set(model.pel.heatersState, Heaters_State.DOWNLINK);
    set(model.pel.harnesslossState, HarnessLoss_State.DOWNLINK);

    delay(duration);

    set(model.telecomModel.downlinkBitRate, 0.0);
    set(model.pel.x_twtaState, X_TWTA_State.OFF);
    set(model.pel.ka_twtaState, Ka_TWTA_State.OFF);
    set(model.pel.ssrState, SSR_State.ON);
    set(model.pel.idstState, IDST_State.ON);
    set(model.pel.propState, Prop_State.OFF);

    if(prevVisarState.equals(VISAR_State.OFF)) {
      set(model.pel.visarState, VISAR_State.OFF);
      set(model.pel.visar_heatersState, VISAR_Heaters_State.SCIENCE_SURVIVAL);
      set(model.pel.heatersState, Heaters_State.SURVIVAL);
      set(model.pel.harnesslossState, HarnessLoss_State.VISAR_OFF);
    } else {
      set(model.pel.visarState, VISAR_State.ON);
      set(model.pel.visar_heatersState, VISAR_Heaters_State.OFF);
      set(model.pel.heatersState, Heaters_State.VISAR_ON);
      set(model.pel.harnesslossState, HarnessLoss_State.VISAR_ON);
    }

    if(prevVemState.equals(VEM_State.OFF)) {
      set(model.pel.vemState, VEM_State.OFF);
      set(model.pel.vem_heatersState, VEM_Heaters_State.SCIENCE_SURVIVAL);
    } else {
      set(model.pel.vemState, VEM_State.ON);
      set(model.pel.vem_heatersState, VEM_Heaters_State.VEM_ON);
    }

    // Send data from s/c to ground

  }

}
