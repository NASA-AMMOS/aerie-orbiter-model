package missionmodel.power.pel;
/**
* This class was created by the pel_java_generator.py script and represents the state(s) of the Radar as an enum and associates
* a power load amount to each state.
*/
public enum Radar_State {
	OFF(0.0, 0.0),
	DOWNLINK(198.2, 198.2),
	ON(543.2, 543.2),
    ON_LOW(1000, 1000),
	ON_MED(2000, 2000),
	ON_HI(4000, 4000);
    private final double cbeload;
    private final double mevload;
    Radar_State(double cbeload, double mevload) {
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
