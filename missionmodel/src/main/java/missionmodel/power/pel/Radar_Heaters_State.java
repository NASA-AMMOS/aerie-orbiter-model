package missionmodel.power.pel;
/**
* This class was created by the pel_java_generator.py script and represents the state(s) of the Radar_Heaters as an enum and associates
* a power load amount to each state.
*/
public enum Radar_Heaters_State {
	OFF(0.0, 0.0),
	CRUISE_SURVIVAL(164.8, 164.8),
	SCIENCE_SURVIVAL(9.5, 9.5);
    private final double cbeload;
    private final double mevload;
    Radar_Heaters_State(double cbeload, double mevload) {
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
