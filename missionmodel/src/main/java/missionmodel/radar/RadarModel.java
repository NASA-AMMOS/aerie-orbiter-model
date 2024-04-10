package missionmodel.radar;

import gov.nasa.jpl.aerie.contrib.serialization.mappers.DoubleValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.EnumValueMapper;
import gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource;
import gov.nasa.jpl.aerie.contrib.streamline.core.Resource;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.Registrar;
import gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete;
import missionmodel.Configuration;

import static gov.nasa.jpl.aerie.contrib.metadata.UnitRegistrar.withUnit;
import static gov.nasa.jpl.aerie.contrib.streamline.core.MutableResource.resource;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.Discrete.discrete;
import static gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.monads.DiscreteResourceMonad.map;

public class RadarModel {

  public MutableResource<Discrete<RadarDataCollectionMode>> RadarDataMode;

  public Resource<Discrete<Double>> RadarDataRate; // kbps

  public RadarModel(Registrar registrar, Configuration config) {
    RadarDataMode = resource(discrete(RadarDataCollectionMode.OFF));
    registrar.discrete("VisarDataMode",RadarDataMode, new EnumValueMapper<>(RadarDataCollectionMode.class));

    RadarDataRate = map(RadarDataMode, RadarDataCollectionMode::getDataRate);
    registrar.discrete("MagDataRate", RadarDataRate, withUnit("Mbps", new DoubleValueMapper()));
  }

}
