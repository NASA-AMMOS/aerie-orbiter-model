package missionmodel.geometry.spiceinterpolation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import missionmodel.Mission;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static missionmodel.config.ConfigObject.jsonObjHasKey;

public class Bodies {

  private JsonObject bodiesJsonObject;
  private HashMap<String, Body> bodies;

  public Bodies() {
    this.bodiesJsonObject = parseBodiesFromJson();
    this.bodies = initializeAllBodiesFromJson(this.bodiesJsonObject);
  }

  public HashMap<String, Body> getBodiesMap(){
    return bodies;
  }

  public JsonObject getBodiesJson(){
    return bodiesJsonObject;
  }

  private JsonObject parseBodiesFromJson() {
    try (
      var in = Objects.requireNonNull(Mission.class.getResourceAsStream("default_geometry_config.json"), "default_geometry_config.json not found");
      var reader = new InputStreamReader(in)
    ) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public HashMap<String, Body> initializeAllBodiesFromJson(JsonObject bodiesJson){
    HashMap<String, Body> toReturn = new HashMap<>();
    JsonObject jsonObject = bodiesJson.get("bodies").getAsJsonObject();

    Set<Map.Entry<String, JsonElement>> entrySet = jsonObject.entrySet();
    for(Map.Entry<String, JsonElement> entry : entrySet){
      JsonObject body = entry.getValue().getAsJsonObject();
      if(jsonObjHasKey(body, "Trajectory")) {
        JsonObject trajectory = body.get("Trajectory").getAsJsonObject();
        toReturn.put(entry.getKey(), new Body(entry.getKey(),
          body.get("NaifID").getAsInt(),
          body.get("NaifFrame").getAsString(),
          body.get("Albedo").getAsDouble(),
          getIfNonNull(trajectory, "calculateAltitude"),
          getIfNonNull(trajectory, "calculateEarthSpacecraftBodyAngle"),
          getIfNonNull(trajectory, "calculateSubSCInformation"),
          getIfNonNull(trajectory, "calculateRaDec"),
          getIfNonNull(trajectory, "calculateIlluminationAngles"),
          getIfNonNull(trajectory, "calculateSubSolarInformation"),
          getIfNonNull(trajectory, "calculateLST"),
          getIfNonNull(trajectory, "calculateBetaAngle"),
          getIfNonNull(trajectory, "calculateOrbitParameters"),
          getIfNonNull(trajectory, "useDSK")));
      }
      else{
        toReturn.put(entry.getKey(), new Body(entry.getKey(),
          body.get("NaifID").getAsInt(),
          body.get("NaifFrame").getAsString(),
          body.get("Albedo").getAsDouble()
        ));
      }
    }

    return toReturn;
  }

  private boolean getIfNonNull(JsonObject obj, String key){
    return obj.get(key) != null && !obj.get(key).isJsonNull() ? obj.get(key).getAsBoolean() : false;
  }

}
