package missionmodel.geometry.spiceinterpolation;

import gov.nasa.jpl.aerie.contrib.streamline.modeling.Registrar;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects;
import missionmodel.AbsoluteClock;
import missionmodel.JPLTimeConvertUtility;
import missionmodel.geometry.directspicecalls.SpiceDirectTimeDependentStateCalculator;
import missionmodel.geometry.interfaces.GeometryCalculator;
import missionmodel.geometry.interfaces.GeometryInformationNotAvailableException;
import missionmodel.geometry.interfaces.TimeDependentStateCalculator;
import missionmodel.geometry.resources.GenericGeometryResources;
import missionmodel.geometry.returnedobjects.*;
import gov.nasa.jpl.time.Time;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import spice.basic.SpiceErrorException;

import java.util.Map;

// import static missionmodel.geometry.directspicecalls.SpiceDirectTimeDependentStateCalculator.et2LSTHours;
import static gov.nasa.jpl.aerie.contrib.streamline.core.Resources.currentValue;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteEffects.set;
import static missionmodel.geometry.directspicecalls.SpiceDirectTimeDependentStateCalculator.et2LSTHours;
import static missionmodel.geometry.resources.GenericGeometryResources.*;

public class GenericGeometryCalculator implements GeometryCalculator {
  protected Map<String, Body> bodies;
  protected final int sc_id;
  protected final String abcorr;
  protected TimeDependentStateCalculator calc;
  protected  AbsoluteClock absClock;

  private  GenericGeometryResources geomRes;

  protected  Registrar errorRegistrar;

  public GenericGeometryCalculator(AbsoluteClock absoluteClock, int sc_id, String abcorr, Registrar errorRegistrar){
    this.absClock = absoluteClock;
    this.sc_id = sc_id;
    this.abcorr = abcorr;
    this.errorRegistrar = errorRegistrar;
  }

  public void setBodies(Map<String, Body> bodies){
    this.bodies = bodies;
    this.calc = new SpiceDirectTimeDependentStateCalculator(bodies, true);
    this.geomRes = new GenericGeometryResources(errorRegistrar, bodies);
  }

  public GenericGeometryResources getResources() {
    return this.geomRes;
  }

  public void calculateGeometry(Body body) throws GeometryInformationNotAvailableException {
    Vector3D[] bodyPositionAndVelocityWRTSpacecraft = calc.getState(JPLTimeConvertUtility.nowJplTime(absClock), Integer.toString(sc_id), body.getName(), abcorr);
    Vector3D[] sunPositionAndVelocityWRTBody = null;

    // calculate some quantities for every body
    set(geomRes.BODY_POS_ICRF.get(body.getName()), bodyPositionAndVelocityWRTSpacecraft[0]);
    set(geomRes.BODY_VEL_ICRF.get(body.getName()), bodyPositionAndVelocityWRTSpacecraft[1]);
    set(geomRes.SpacecraftBodyRange.get(body.getName()), bodyPositionAndVelocityWRTSpacecraft[0].getNorm());
    set(geomRes.SpacecraftBodySpeed.get(body.getName()), bodyPositionAndVelocityWRTSpacecraft[1].getNorm());
    set(geomRes.BodyHalfAngleSize.get(body.getName()), Math.asin(body.getAverageEquitorialRadius()/bodyPositionAndVelocityWRTSpacecraft[0].getNorm())*(180.0/Math.PI));

    // this section is also multi-mission; the Sun can't have an angle from itself
    if(!body.getName().equals("SUN")){
      sunPositionAndVelocityWRTBody = calc.getState(JPLTimeConvertUtility.nowJplTime(absClock), body.getName(), "SUN", abcorr);
      set(geomRes.SunSpacecraftBodyAngle.get(body.getName()), Vector3D.angle(bodyPositionAndVelocityWRTSpacecraft[0].add(sunPositionAndVelocityWRTBody[0]),bodyPositionAndVelocityWRTSpacecraft[0])*(180.0/Math.PI));
      set(geomRes.SunBodySpacecraftAngle.get(body.getName()), Vector3D.angle(bodyPositionAndVelocityWRTSpacecraft[0].scalarMultiply(-1.0), sunPositionAndVelocityWRTBody[0])*(180.0/Math.PI));
    }

    // this section is multi-mission because all missions have to communicate with Earth
    if(body.getName().equals("EARTH")) {
      set(geomRes.upleg_time, Time.upleg( JPLTimeConvertUtility.nowJplTime(absClock),
        sc_id, bodies.get("EARTH").getNAIFID()).totalSeconds());
      set(geomRes.downleg_time, Time.downleg(JPLTimeConvertUtility.nowJplTime(absClock),
        sc_id, bodies.get("EARTH").getNAIFID()).totalSeconds());
      RADec scRADec = new RADec(currentValue(geomRes.BODY_POS_ICRF.get("EARTH")).negate() , new Vector3D(0.0, 0.0, 0.0));
      set(geomRes.spacecraftDeclination, scRADec.getDec());
      set(geomRes.spacecraftRightAscension, scRADec.getRA());
      set(geomRes.EarthSunProbeAngle, 180.0 - (currentValue(geomRes.SunBodySpacecraftAngle.get("EARTH")) +
        currentValue(geomRes.SunSpacecraftBodyAngle.get("EARTH"))));
    }

    // then we calculate things depending if the body was initialized to ask for it
    if(body.doCalculateRaDec()){
      Vector3D[] bodyPositionAndVelocityWRTEarth = calc.getState(JPLTimeConvertUtility.nowJplTime(absClock),
        "EARTH", body.getName(), abcorr);
      RADec earthRaDec = new RADec(bodyPositionAndVelocityWRTEarth[0], new Vector3D(0.0,0.0,0.0));
      set(geomRes.EarthRaDecByBody.get(body.getName()).get("Ra"), earthRaDec.getRA());
      set(geomRes.EarthRaDecByBody.get(body.getName()).get("Dec"), earthRaDec.getDec());

      double spacecraftRAFromEarth = currentValue(geomRes.spacecraftRightAscension);
      double bodyRAFromEarth = earthRaDec.getRA();
      set(geomRes.EarthRaDeltaWithSCByBody.get(body.getName()),
        Math.min(Math.min(Math.abs(spacecraftRAFromEarth - bodyRAFromEarth),
            Math.abs(spacecraftRAFromEarth - bodyRAFromEarth + 360)),
          Math.abs(spacecraftRAFromEarth - bodyRAFromEarth - 360)));
    }

    if(body.doCalculateEarthSpacecraftBodyAngle()){
      Vector3D[] earthPositionAndVelocityWRTSC = calc.getState(JPLTimeConvertUtility.nowJplTime(absClock),
        Integer.toString(sc_id), "EARTH", abcorr);
      // this also comes in as radians and we want degrees
      set(geomRes.EarthSpacecraftBodyAngle.get(body.getName()), Vector3D.angle(earthPositionAndVelocityWRTSC[0],
        currentValue(geomRes.BODY_POS_ICRF.get(body.getName())))*(180.0/Math.PI));
    }

    if(body.doCalculateBetaAngle() && !body.getName().equals("SUN")){
      // beta angle is the angle between the vector normal to the orbital plane (sc position x velocity) and the
      // vector from the body to the sun
      Vector3D orbitPlaneNormal = bodyPositionAndVelocityWRTSpacecraft[0].crossProduct(bodyPositionAndVelocityWRTSpacecraft[1]).normalize();
      set(geomRes.BetaAngleByBody.get(body.getName()), (Vector3D.angle(orbitPlaneNormal, sunPositionAndVelocityWRTBody[0].negate())*(180.0/Math.PI))-90);
    }

    if(body.doCalculateSubSolarInformation() && !body.getName().equals("SUN")){
      SubPointInformation sp_sun = calc.getSubPointInformation(JPLTimeConvertUtility.nowJplTime(absClock),
        "SUN", body.getName(), abcorr, body.useDSK());
      LatLonCoord latLonSolarData = new LatLonCoord(sp_sun.getSpoint());
      // noone talks in radians lat/lon, so we convert to degrees
      set(geomRes.BodySubSolarPoint.get(body.getName()), new Vector3D(
        latLonSolarData.getLatitude()*(180.0/Math.PI),
        latLonSolarData.getLongitude()*(180.0/Math.PI),
           latLonSolarData.getRadius()));
    }

    if(body.doCalculateSubSCPoint() || body.doCalculateIlluminationAngles() || body.doCalculateAltitude()){
      SubPointInformation sp_sc = calc.getSubPointInformation(JPLTimeConvertUtility.nowJplTime(absClock),
        Integer.toString(sc_id), body.getName(), abcorr, body.useDSK());
      if(sp_sc.isFound()) {
        if(body.doCalculateSubSCPoint() || body.doCalculateAltitude()) {
          LatLonCoord latLonSurfaceData = new LatLonCoord(sp_sc.getSpoint());
          set(geomRes.BodySubSCPoint.get(body.getName()).get("dist"), sp_sc.getSrfvec().getNorm());
          // noone talks in radians lat/lon, so we convert to degrees
          set(geomRes.BodySubSCPoint.get(body.getName()).get("latitude"), latLonSurfaceData.getLatitude()*(180.0/Math.PI));
          set(geomRes.BodySubSCPoint.get(body.getName()).get("longitude"), latLonSurfaceData.getLongitude()*(180.0/Math.PI));
          set(geomRes.BodySubSCPoint.get(body.getName()).get("radius"), latLonSurfaceData.getRadius());
          if(body.doCalculateAltitude()){
            set(geomRes.SpacecraftAltitude.get(body.getName()),
              bodyPositionAndVelocityWRTSpacecraft[0].getNorm()-latLonSurfaceData.getRadius());
          }

          if(body.doCalculateLST()){
            try {
              set(geomRes.BodySubSCPoint.get(body.getName()).get("LST"),
                et2LSTHours(JPLTimeConvertUtility.nowJplTime(absClock), body.getNAIFID(), latLonSurfaceData.getLongitude()));
            } catch (SpiceErrorException e) {
              throw new GeometryInformationNotAvailableException(e.getMessage());
            }
          }
        }

        if (body.doCalculateIlluminationAngles()) {
          IlluminationAngles illumAngles = calc.getIlluminationAngles(JPLTimeConvertUtility.nowJplTime(absClock),
            Integer.toString(sc_id), body.getName(), abcorr, body.useDSK());
          set(geomRes.IlluminationAnglesByBody.get(body.getName()).get("phase"), illumAngles.getPhaseAngle());
          set(geomRes.IlluminationAnglesByBody.get(body.getName()).get("incidence"), illumAngles.getIncidenceAngle());
          set(geomRes.IlluminationAnglesByBody.get(body.getName()).get("emission"), illumAngles.getEmissionAngle());
        }
      }
    }

    if(body.doCalculateOrbitParameters()){
      OrbitConicElements SCOrbitOfBody = calc.getOrbitConicElements(JPLTimeConvertUtility.nowJplTime(absClock),
        Integer.toString(sc_id), body.getName(), abcorr);
      // we only want to set inclination and orbit period if eccentricity is less than 1, because otherwise we're not actually in orbit and we get NaN for orbit period
      if(SCOrbitOfBody.getEccentricity() < 1) {
        double semiMajorAxis = SCOrbitOfBody.getPerifocalDistance() / (1 - SCOrbitOfBody.getEccentricity());
        set(geomRes.orbitInclinationByBody.get(body.getName()), SCOrbitOfBody.getInclination() * (180.0 / Math.PI));
        set(geomRes.orbitPeriodByBody.get(body.getName()), 2 * Math.PI * Math.sqrt(Math.pow(semiMajorAxis, 3) / body.getMu()));
      }
    }

  }

//  public static Vector3D positionResourceToVector3D(String body) {
//    return new Vector3D(
//      currentValue(geomRes.BODY_POS_ICRF.get(body);
//      BODY_POS_ICRF.get(body).get("x").currentval(),
//      BODY_POS_ICRF.get(body).get("y").currentval(),
//      BODY_POS_ICRF.get(body).get("z").currentval()
//    );
//  }
}
