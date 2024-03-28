package missionmodel.geometry.resources;

import gov.nasa.jpl.aerie.contrib.serialization.mappers.*;
import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.Registrar;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete;
import missionmodel.geometry.spiceinterpolation.Body;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static gov.nasa.jpl.aerie.contrib.metadata.UnitRegistrar.withUnit;
import static gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource.resource;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete.discrete;

public class GenericGeometryResources {

  public static final double FLOAT_EPSILON = 0.0001;
  private static Map<String, Body> bodyObjects;
  private static String[] bodies;
  private static List<String> earthSpacecraftBodies;
  private static List<String> altitudeBodies;
  private static List<String> illuminationBodies;
  private static List<String> raDecBodies;
  private static List<String> subSolarBodies;
  private static List<String> subSCBodies;
  private static List<String> betaAngleBodies;
  private static List<String> orbitParameterBodies;

  //private static String[] cartesian = new String[]{"x","y","z"};
  private static String[] illumAngles = new String[]{"phase","incidence","emission"};
  private static String[] raDecIndices = new String[]{"Ra", "Dec"};
  //private static String[] latLonCoordinates = new String[]{"latitude","longitude", "radius"};
  private static String[] subSCIndices = new String[]{"dist","latitude","longitude","radius","LST"};

  public static Map<String, String> ComplexRepresentativeStation = new HashMap<>();
  static{
    ComplexRepresentativeStation.put("Goldstone", "DSS-24");
    ComplexRepresentativeStation.put("Canberra", "DSS-34");
    ComplexRepresentativeStation.put("Madrid", "DSS-54");
  }

  public MutableResource<Discrete<Double>> upleg_time;
  public MutableResource<Discrete<Double>> downleg_time;
  public MutableResource<Discrete<Double>> spacecraftDeclination;
  public MutableResource<Discrete<Double>> spacecraftRightAscension;
  public Map<String, MutableResource<Discrete<Vector3D>>> BODY_POS_ICRF;
  public Map<String, MutableResource<Discrete<Vector3D>>> BODY_VEL_ICRF;
  public Map<String, MutableResource<Discrete<Double>>> SpacecraftBodyRange;
  public Map<String, MutableResource<Discrete<Double>>> SpacecraftBodySpeed;
  public Map<String, MutableResource<Discrete<Double>>> SunSpacecraftBodyAngle;
  public Map<String, MutableResource<Discrete<Double>>> SunBodySpacecraftAngle;
  public Map<String, MutableResource<Discrete<Double>>> BodyHalfAngleSize;

  public Map<String, MutableResource<Discrete<Double>>> BetaAngleByBody;

  public Map<String, MutableResource<Discrete<Double>>> EarthSpacecraftBodyAngle;

  public MutableResource<Discrete<Double>> EarthSunProbeAngle;

  public Map<String, MutableResource<Discrete<Double>>> SpacecraftAltitude;

  public Map<String, Map<String, MutableResource<Discrete<Double>>>> IlluminationAnglesByBody;

  public Map<String, Map<String, MutableResource<Discrete<Double>>>> EarthRaDecByBody;

  public Map<String, MutableResource<Discrete<Double>>> EarthRaDeltaWithSCByBody;

  public Map<String, MutableResource<Discrete<Vector3D>>> BodySubSolarPoint;
  public Map<String, Map<String, MutableResource<Discrete<Double>>>> BodySubSCPoint;

  public Map<String, MutableResource<Discrete<EclipseTypes>>> SpacecraftEclipseByBody;
  public MutableResource<Discrete<EclipseTypes>> AnySpacecraftEclipse;

  public Map<String, Map<String, MutableResource<Discrete<Boolean>>>> SpacecraftOccultationByBodyAndStation;
  public MutableResource<Discrete<Integer>> Occultation;
  public MutableResource<Discrete<Double>> FractionOfSunNotInEclipse;
  public MutableResource<Discrete<Integer>> LitOrDarkSide;

  public Map<String, MutableResource<Discrete<Double>>> orbitInclinationByBody;
  public Map<String, MutableResource<Discrete<Double>>> orbitPeriodByBody;


  public Map<String, MutableResource<Discrete<Boolean>>> Periapsis;
  public Map<String, MutableResource<Discrete<Boolean>>> Apoapsis;

  public GenericGeometryResources(Registrar registrar, Map<String, Body> allBodies) {
    bodyObjects = allBodies;
    bodies = Body.getNamesOfBodies(allBodies);
    earthSpacecraftBodies =  Body.getEarthSCBodies(allBodies);
    altitudeBodies = Body.getAltitudeBodies(allBodies);
    illuminationBodies = Body.getIlluminationAngleBodies(allBodies);
    raDecBodies = Body.getRaDecBodies(allBodies);
    subSolarBodies = Body.getSubSolarBodies(allBodies);
    subSCBodies = Body.getRadiatorAvoidanceBodies(allBodies);
    betaAngleBodies = Body.getBetaAngleBodies(allBodies);
    orbitParameterBodies = Body.getOrbitParameterBodies(allBodies);

    // Initialize resources
    BODY_POS_ICRF = new HashMap<>();
    BODY_VEL_ICRF = new HashMap<>();
    SpacecraftBodyRange = new HashMap<>();
    SpacecraftBodySpeed = new HashMap<>();
    SunSpacecraftBodyAngle = new HashMap<>();
    SunBodySpacecraftAngle = new HashMap<>();
    BodyHalfAngleSize = new HashMap<>();
    IlluminationAnglesByBody = new HashMap<>();
    EarthRaDecByBody = new HashMap<>();
    BodySubSCPoint = new HashMap<>();
    SpacecraftOccultationByBodyAndStation = new HashMap<>();
    SpacecraftEclipseByBody = new HashMap<>();
    BetaAngleByBody = new HashMap<>();
    EarthSpacecraftBodyAngle = new HashMap<>();
    SpacecraftAltitude = new HashMap<>();
    EarthRaDeltaWithSCByBody = new HashMap<>();
    BodySubSolarPoint = new HashMap<>();
    orbitInclinationByBody = new HashMap<>();
    orbitPeriodByBody = new HashMap<>();

    Apoapsis = new HashMap<>();
    Periapsis = new HashMap<>();

    // Non-arrayed resources
    upleg_time = resource(discrete(0.0));
    registrar.discrete("upleg_time", upleg_time, new DoubleValueMapper());

    downleg_time = resource(discrete(0.0));
    registrar.discrete("downleg_time", downleg_time, new DoubleValueMapper());

    spacecraftDeclination = resource(discrete(0.0));
    registrar.discrete("spacecraftDeclination", spacecraftDeclination, new DoubleValueMapper());

    spacecraftRightAscension = resource(discrete(0.0));
    registrar.discrete("spacecraftRightAscension", spacecraftRightAscension, new DoubleValueMapper());

    EarthSunProbeAngle = resource(discrete(0.0));
    registrar.discrete("EarthSunProbeAngle", EarthSunProbeAngle, new DoubleValueMapper());

    AnySpacecraftEclipse = resource(discrete(EclipseTypes.NONE));
    registrar.discrete("AnySpacecraftEclipse", AnySpacecraftEclipse, new EnumValueMapper(EclipseTypes.class));

    Occultation = resource(discrete(0));
    registrar.discrete("Occultation", Occultation, new IntegerValueMapper());

    FractionOfSunNotInEclipse = resource(discrete(1.0));
    registrar.discrete("FractionOfSunNotInEclipse", FractionOfSunNotInEclipse, new DoubleValueMapper());

    LitOrDarkSide = resource(discrete(0));
    registrar.discrete("LitOrDarkSide", LitOrDarkSide, new IntegerValueMapper());

    // loop through bodies to build and register arrayed resources
    for (String body : bodies) {
      BODY_POS_ICRF.put(body, resource(discrete( new Vector3D(0.0,0.0,0.0))));
      registrar.discrete("BODY_POS_ICRF_" + body, BODY_POS_ICRF.get(body), new Vector3DValueMapper(new DoubleValueMapper()));

      BODY_VEL_ICRF.put(body, resource(discrete( new Vector3D(0.0,0.0,0.0))));
      registrar.discrete("BODY_VEL_ICRF_" + body, BODY_VEL_ICRF.get(body), new Vector3DValueMapper(new DoubleValueMapper()));

      SpacecraftBodyRange.put(body, resource(discrete(0.0)));
      registrar.discrete("SpacecraftBodyRange_" + body, SpacecraftBodyRange.get(body), withUnit("km", new DoubleValueMapper()));

      SpacecraftBodySpeed.put(body, resource(discrete(0.0)));
      registrar.discrete("SpacecraftBodySpeed_" + body, SpacecraftBodySpeed.get(body), withUnit("km/s", new DoubleValueMapper()));

      SunSpacecraftBodyAngle.put(body, resource(discrete(0.0)));
      registrar.discrete("SunSpacecraftBodyAngle_" + body, SunSpacecraftBodyAngle.get(body), withUnit("deg", new DoubleValueMapper()));

      SunBodySpacecraftAngle.put(body, resource(discrete(0.0)));
      registrar.discrete("SunBodySpacecraftAngle_" + body, SunBodySpacecraftAngle.get(body), withUnit("deg", new DoubleValueMapper()));

      BodyHalfAngleSize.put(body, resource(discrete(0.0)));
      registrar.discrete("BodyHalfAngleSize_" + body, BodyHalfAngleSize.get(body), withUnit("deg", new DoubleValueMapper()));

      if (betaAngleBodies.contains(body)) {
        BetaAngleByBody.put(body, resource(discrete(0.0)));
        registrar.discrete("BetaAngle_" + body, BetaAngleByBody.get(body), withUnit("deg", new DoubleValueMapper()));
      }

      if (earthSpacecraftBodies.contains(body)) {
        EarthSpacecraftBodyAngle.put(body, resource(discrete(0.0)));
        registrar.discrete("EarthSpacecraftAngle_" + body, EarthSpacecraftBodyAngle.get(body), withUnit("deg", new DoubleValueMapper()));
      }

      if (altitudeBodies.contains(body)) {
        SpacecraftAltitude.put(body, resource(discrete(0.0)));
        registrar.discrete("SpacecraftAltitude_" + body, SpacecraftAltitude.get(body), withUnit("km", new DoubleValueMapper()));
      }

      if (illuminationBodies.contains(body)) {
        Map<String, MutableResource<Discrete<Double>>> illumAnglesMap = new HashMap<>();
        for (String angle : illumAngles) {
          illumAnglesMap.put(angle, resource(discrete(0.0)));
          registrar.discrete("IlluminationAnglesByBody_" + body + "_" + angle,
            illumAnglesMap.get(angle), withUnit("deg", new DoubleValueMapper()));
        }
        IlluminationAnglesByBody.put(body, illumAnglesMap);
      }

      if (raDecBodies.contains(body)) {
        Map<String, MutableResource<Discrete<Double>>> EarthRaDecMap = new HashMap<>();
        for (String angle : raDecIndices) {
          EarthRaDecMap.put(angle, resource(discrete(0.0)));
          registrar.discrete("EarthRaDecByBody_" + body + "_" + angle,
            EarthRaDecMap.get(angle), withUnit("deg", new DoubleValueMapper()));
        }
        EarthRaDecByBody.put(body, EarthRaDecMap);
        EarthRaDeltaWithSCByBody.put(body, resource(discrete(0.0)));
        registrar.discrete("EarthRaDeltaWithSCByBody_" + body, EarthRaDeltaWithSCByBody.get(body), withUnit("deg", new DoubleValueMapper()));
      }

      if (subSolarBodies.contains(body)) {
        BodySubSolarPoint.put(body, resource(discrete( new Vector3D(0.0,0.0,0.0))));
        registrar.discrete("BodySubSolarPoint_" + body, BodySubSolarPoint.get(body), new Vector3DValueMapper(new DoubleValueMapper()));
      }

      if (subSCBodies.contains(body)) {
        Map<String, MutableResource<Discrete<Double>>> subSCMap = new HashMap<>();
        for (String index : subSCIndices) {
          subSCMap.put(index, resource(discrete(0.0)));
          registrar.discrete("subSCBodies_" + body + "_" + index,
            subSCMap.get(index), new DoubleValueMapper());
        }
        BodySubSCPoint.put(body, subSCMap);
      }

      SpacecraftEclipseByBody.put(body, resource(discrete(EclipseTypes.NONE)));
      registrar.discrete("SpacecraftEclipseByBody_" + body,
        SpacecraftEclipseByBody.get(body), new EnumValueMapper<>(EclipseTypes.class));

      Map<String, MutableResource<Discrete<Boolean>>> occultationStationMap = new HashMap<>();
      for (Map.Entry<String,String> entry : ComplexRepresentativeStation.entrySet()) {
        occultationStationMap.put(entry.getValue(), resource(discrete(false)));
        registrar.discrete("IlluminationAnglesByBody_" + body + "_" + entry.getKey(),
          occultationStationMap.get(entry.getValue()), new BooleanValueMapper());
      }
      SpacecraftOccultationByBodyAndStation.put(body, occultationStationMap);

      if (orbitParameterBodies.contains(body)) {
        orbitInclinationByBody.put(body, resource(discrete(0.0)));
        registrar.discrete("orbitInclinationByBody_" + body, orbitInclinationByBody.get(body), withUnit("deg", new DoubleValueMapper()));

        orbitPeriodByBody.put(body, resource(discrete(0.0)));
        registrar.discrete("orbitPeriodByBody_" + body, orbitPeriodByBody.get(body), withUnit("s", new DoubleValueMapper()));
      }

      Periapsis.put(body, resource(discrete(false)));
      registrar.discrete("Periapsis_" + body, Periapsis.get(body), new BooleanValueMapper());

      Apoapsis.put(body, resource(discrete(false)));
      registrar.discrete("Apoapsis_" + body, Apoapsis.get(body), new BooleanValueMapper());
    }

  }
  public static Map<String, Body> getBodies(){
    return bodyObjects;
  }


}
