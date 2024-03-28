package missionmodel.power.pel;
/**
* This class was created by the pel_java_generator.py script and represents the state(s) of the SSR as an enum and associates
* a power load amount to each state.
*/
public enum SSR_State {
	OFF(0.0, 0.0),
	ON(19.2, 19.2),
	DOWNLINK(21.4, 21.4);
    private final double cbeload;
    private final double mevload;
    SSR_State(double cbeload, double mevload) {
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
