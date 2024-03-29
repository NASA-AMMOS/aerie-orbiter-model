package missionmodel.visar;

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

public class VisarModel {

  public MutableResource<Discrete<VisarDataCollectionMode>> VisarDataMode;

  public Resource<Discrete<Double>> VisarDataRate; // kbps

  public VisarModel(Registrar registrar, Configuration config) {
    VisarDataMode = resource(discrete(VisarDataCollectionMode.OFF));
    registrar.discrete("VisarDataMode",VisarDataMode, new EnumValueMapper<>(VisarDataCollectionMode.class));

    VisarDataRate = map(VisarDataMode, VisarDataCollectionMode::getDataRate);
    registrar.discrete("MagDataRate", VisarDataRate, withUnit("Mbps", new DoubleValueMapper()));
  }

}
