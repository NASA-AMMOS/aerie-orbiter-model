package missionmodel.radar;

public enum RadarDataCollectionMode {
  OFF(0.0), // Mbps
  LOW_RES(0.1),
  MED_RES(1.0),
  HI_RES(4.0);

  private final double radarDataRate;

  RadarDataCollectionMode(double radarDataRate) {
    this.radarDataRate = radarDataRate;
  }

  public double getDataRate() {
    return radarDataRate;
  }
}
