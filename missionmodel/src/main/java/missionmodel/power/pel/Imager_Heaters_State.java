package missionmodel.power.pel;
/**
* This class was created by the pel_java_generator.py script and represents the state(s) of the Imager_Heaters as an enum and associates
* a power load amount to each state.
*/
public enum Imager_Heaters_State {
	OFF(0.0, 0.0),
	CRUISE_SURVIVAL(123.1, 123.1),
	IMAGER_ON(92.4, 92.4),
	DOWNLINK(14.0, 14.0),
	SCIENCE_SURVIVAL(105.2, 105.2);
    private final double cbeload;
    private final double mevload;
    Imager_Heaters_State(double cbeload, double mevload) {
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
