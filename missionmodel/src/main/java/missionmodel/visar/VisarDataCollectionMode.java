package missionmodel.visar;

public enum VisarDataCollectionMode {
  OFF(0.0), // Mbps
  DEM(.095),
  MED_RES(1.199),
  HI_RES(4.51),
  RPI(74.102);

  private final double visarDataRate;

  VisarDataCollectionMode(double visarDataRate) {
    this.visarDataRate = visarDataRate;
  }

  public double getDataRate() {
    return visarDataRate;
  }
}
