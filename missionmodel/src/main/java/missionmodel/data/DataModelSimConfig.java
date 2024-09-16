package missionmodel.data;

import gov.nasa.jpl.aerie.merlin.framework.annotations.AutoValueMapper;

import static gov.nasa.jpl.aerie.merlin.framework.annotations.Export.Template;

@AutoValueMapper.Record
public record DataModelSimConfig(double initialMaxVolume,
                                 double initialDatarate) {

  public static final double DEFAULT_INIT_MAX_VOLUME = 50e9; // 50 Gb

  public static final double DEFAULT_INIT_PLAYBACK_RATE = 1e6; // 1 Mbps

  public static @Template DataModelSimConfig defaultConfiguration() {
    return new DataModelSimConfig(DEFAULT_INIT_MAX_VOLUME,
      DEFAULT_INIT_PLAYBACK_RATE);
  }
}
