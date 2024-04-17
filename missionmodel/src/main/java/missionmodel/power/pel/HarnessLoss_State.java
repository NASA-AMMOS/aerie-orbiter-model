package missionmodel.power.pel;
/**
* This class was created by the pel_java_generator.py script and represents the state(s) of the HarnessLoss as an enum and associates
* a power load amount to each state.
*/
public enum HarnessLoss_State {
	OFF(0.0, 0.0),
	DOWNLINK(38.4, 38.4),
	RADAR_ON(38.2, 38.2),
	RADAR_OFF(16.2, 16.2),
	TCM(61.9, 61.9);
    private final double cbeload;
    private final double mevload;
    HarnessLoss_State(double cbeload, double mevload) {
        this.cbeload = cbeload;  //in Watts
        this.mevload = mevload; //in Watts
    }

    /**
    * Function that returns the cbe load of state of the instrument.
    * @return the power needed for that state
    */
    public double getCBELoad() {
        return cbeload;
    }

    /**
    * Function that returns the mev load of state of the instrument.
    * @return the power needed for that state
    */
    public double getMEVLoad() {
        return mevload;
    }
}
