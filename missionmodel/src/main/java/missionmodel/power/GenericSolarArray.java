package missionmodel.power;

import gov.nasa.jpl.aerie.contrib.serialization.mappers.DoubleValueMapper;
import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.core.Resource;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.Registrar;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete;

import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects.set;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.monads.DiscreteResourceMonad.bind;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.monads.DiscreteResourceMonad.map;

/**
 * This class represents the power generation for the spacecraft using a solar array. The solar array is associated with
 * a battery model, and it supplies power to the spacecraft and the battery. It is part of the overall power system.
 */

public class GenericSolarArray {
    public final double SOLAR_INTENSITY_AT_EARTH = 1360.8; //solar irradiance from the sun at 1 AU (W/m^2)
    public MutableResource<Discrete<ArrayDeploymentStates>> solarArrayDeploymentState; //State of solar array deployment
    public Resource<Discrete<Double>> powerProduction;   //total power produced by the solar arrays (W)
    public Resource<Discrete<Double>> solarDistance;  //spacecraft distance from the Sun (AU)
    public Resource<Discrete<Double>> arrayToSunAngle;  //angle between the Sun and the array surface normal vector due to spacecraft orientation (deg)
    public Resource<Discrete<Double>> eclipseFactor;  //fraction of the solar irradiance lost due to a spacecraft eclipse
    public MutableResource<Discrete<Double>> arrayCellArea;  //area of the solar arrays containing solar cells (m^2) that can produce power
    public SolarArraySimConfig simConfig;
    public double staticArrayLosses; // Array losses that we do not expect to change with sim time

    /**
     * Constructor for the solar array
     * @param arraySimConfig solar array sim configuration parameters
     * @param solarDistance resource tracking solar distance over time
     * @param arrayToSunAngle resource tracking array to Sun angle over time
     */
    public GenericSolarArray(SolarArraySimConfig arraySimConfig, Resource<Discrete<Double>> solarDistance, Resource<Discrete<Double>> arrayToSunAngle, Resource<Discrete<Double>> eclipseFactor) {
        this.simConfig = arraySimConfig;
        this.solarArrayDeploymentState = MutableResource.resource( Discrete.discrete(simConfig.deploymentState()));
        this.solarDistance = solarDistance;
        this.arrayToSunAngle = arrayToSunAngle;
        this.eclipseFactor = eclipseFactor;
        this.arrayCellArea =  MutableResource.resource( Discrete.discrete( simConfig.arrayMechArea() * simConfig.packingFactor()));

        this.powerProduction = bind(this.solarDistance, distance ->
                               bind(this.arrayCellArea, cellArea ->
                               bind(this.arrayToSunAngle, arrayAngle ->
                                 bind(this.eclipseFactor, eclipseLoss ->
                               map(this.solarArrayDeploymentState, deploymentState ->
                                       computeSolarPower(distance, cellArea, arrayAngle, eclipseLoss, deploymentState) )))));

        //map(this.solarDistance, this.arrayToSunAngle, this.solarArrayDeploymentState, this::computeSolarPower);



        this.staticArrayLosses = simConfig.cellEfficiency() *
                                 simConfig.conversionEfficiency() *
                                 simConfig.otherLosses();
    }

    /**
     * Computes the solar power generated by the array associated with the battery based on distance, arrayToSunAngle, arrayCellArea of the
     * array, and solar intensity - value changes based on whether the solar array is fully deployed or not
     * Since net power is dependent on this, when solar power value changes so does the net power value
     * @return the solar power
     */
    public Double computeSolarPower(Double distance, Double cellArea, Double arrayAngle, Double eclipseLoss, ArrayDeploymentStates deploymentState) {
        if (deploymentState == ArrayDeploymentStates.DEPLOYED) {
            return (SOLAR_INTENSITY_AT_EARTH / (distance * distance) *
                    cellArea *
                    staticArrayLosses *
                    Math.cos(Math.toRadians(arrayAngle)) *
                    eclipseLoss);
        } else {
            return 0.0;
        }
    }

    /**
     * Method to set the deployment state of the solar array
     */
    public void setSolarArrayDeploymentState(ArrayDeploymentStates newState) {
        set(this.solarArrayDeploymentState, newState);
    }

    /**
     * Method for Aerie to register the resources in this model
     * @param registrar how Aerie knows what the resources are
     */
    public void registerStates(Registrar registrar) {
        registrar.discrete("array.powerProduction", powerProduction, new DoubleValueMapper());
        registrar.discrete("spacecraft.solarDistance", solarDistance, new DoubleValueMapper());
        registrar.discrete("spacecraft.arrayToSunAngle", arrayToSunAngle, new DoubleValueMapper());
    }
}
