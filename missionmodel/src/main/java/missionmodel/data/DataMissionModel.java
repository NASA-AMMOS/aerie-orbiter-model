package missionmodel.data;

import java.util.Random;

/**
 * Interface into the model for activities to get data objects and resources
 */
public interface DataMissionModel {
  Data getData();
  Random getRandom();
}
