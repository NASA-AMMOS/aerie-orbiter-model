package missionmodel.config;

import com.google.gson.*;
import missionmodel.Window;
import gov.nasa.jpl.time.Duration;
import gov.nasa.jpl.time.EpochRelativeTime;
import gov.nasa.jpl.time.Time;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static missionmodel.config.PathUtils.fileMissing;
import static missionmodel.config.PathUtils.getAbsolutePath;

public class ConfigObject {

  private JsonObject jsonObject;
  /**
   * Imports the config json and stores the json object.
   */
  public ConfigObject(String configFilePath) {
    try(FileReader fr = new FileReader(configFilePath)){
      this.jsonObject = JsonParser.parseReader(fr).getAsJsonObject();
    }
    catch(JsonIOException | JsonSyntaxException | IOException e){
      throw new RuntimeException("Error parsing config JSON. There may be something wrong with the format of the file, " +
        "such as a missing or extra comma or bracket. See error below:\n\n\n" + e.toString());
    }
  }

  /**
   * Creates a config object from an already-parsed ConfigObject
   * @param jsonObject
   */
  public ConfigObject(JsonObject jsonObject){
    this.jsonObject = jsonObject;
  }

  /**
   * Used to create a sub-'ConfigObject' from a value in higher-level ConfigObject
   * @param objectFieldName
   * @return
   */
  public ConfigObject getConfigObject(String objectFieldName){
    return new ConfigObject(getJSONObject(objectFieldName));
  }

  /**
   * Gets a field from a json object and returns the field as a json object and
   * handles error reporting.
   */
  public JsonObject getJSONObject(String objectFieldName)  {
    if(jsonObjHasKey(jsonObject, objectFieldName) && jsonObject.get(objectFieldName).isJsonObject()){
      return this.jsonObject.get(objectFieldName).getAsJsonObject();
    }
    else{
      throw new RuntimeException("Error parsing config file for field " + objectFieldName + ". " +
        "Could not get as JsonObject. Check that the field is present in the config " +
        "file and not malformed or null.");
    }
  }

  /**
   * Gets a field from a json object and returns the field as a json array and
   * handles error reporting.
   */
  public JsonArray getJSONArray(String arrayFieldName) {
    if(jsonObjHasKey(jsonObject, arrayFieldName) && jsonObject.get(arrayFieldName).isJsonArray()){
      return this.jsonObject.get(arrayFieldName).getAsJsonArray();
    }
    else{
      throw new RuntimeException("Error parsing config file for field " + arrayFieldName + ". " +
        "Could not get as JsonArray. Check that the field is present in the config " +
        "file and not malformed or null.");
    }
  }

  public List<ConfigObject> getConfigObjectsFromJSONArray(String arrayFieldName){

    List<ConfigObject> configObjects = new ArrayList<>();
    JsonArray jsonArray = this.getJSONArray(arrayFieldName);

    for (JsonElement object : jsonArray){
      configObjects.add(new ConfigObject(object.getAsJsonObject()));
    }
    return configObjects;
  }

  /**
   * This method converts an input json object to a string which represents a path to a file,
   * and checks that the file exists. If it doesn't then an error is thrown.
   * The prependToPathString variable is added to the front of the path string.
   */
  public String getPathString(String objectFieldName) {
    String pathString = getAbsolutePath(getString(objectFieldName));

    // now that we have the path string, check if the file `exists.
    if (fileMissing(pathString)) {
      throw new RuntimeException("Error parsing config JSON. The file " + pathString + " in JSON field "
        + objectFieldName + " does not exist or is not readable.\n");
    }
    return pathString;
  }

  /**
   * This method converts an input json object to a string and handles error reporting.
   */
  public String getString(String objectFieldName) {
    if(jsonObjHasKey(jsonObject, objectFieldName)){
      return this.jsonObject.get(objectFieldName).getAsString();
    }
    else{
      throw new RuntimeException("Error parsing string for field " + objectFieldName + " in " +
        "config JSON file. Check that field exists and is not null or mistyped.\n");
    }
  }

  /**
   * This method converts an input json object to a duration and handles error reporting.
   */
  public Duration getDuration(String objectFieldName) {
    try {
      return new Duration(getString(objectFieldName));
    }
    catch (RuntimeException e) {
      throw new RuntimeException("Error parsing duration value from config JSON for field " +
        objectFieldName + ".\n\n" + e.toString());
    }
  }

  /**
   * This method converts an json object string to a time and handles error reporting.
   */
  public Time getTime(String objectFieldName) {
    try {
      return EpochRelativeTime.getAbsoluteOrRelativeTime(getString(objectFieldName));
    }
    catch (RuntimeException e) {
      throw new RuntimeException("Error parsing time value from config JSON for field " +
        objectFieldName + ".\n\n" + e.toString());
    }
  }

  /**
   * This method converts a json object to a boolean and handles error reporting.
   */
  public boolean getBoolean(String objectFieldName) {
    try {
      if(!jsonObjHasKey(jsonObject, objectFieldName)){
        throw new NullPointerException();
      }
      String val = jsonObject.get(objectFieldName).getAsString();
      if(!val.equalsIgnoreCase("true") && !val.equalsIgnoreCase("false")){
        throw new NullPointerException();
      }
      return this.jsonObject.get(objectFieldName).getAsBoolean();
    }
    catch (NullPointerException e) {
      throw new RuntimeException("Error parsing boolean value for field " + objectFieldName + " in " +
        "config JSON file. Value must be true or false.\n");
    }
  }

  /**
   * This method converts a json object to an int and handles error reporting.
   */
  public int getInteger(String objectFieldName) {
    try {
      if(!jsonObjHasKey(jsonObject, objectFieldName)){
        throw new NullPointerException();
      }
      return this.jsonObject.get(objectFieldName).getAsInt();
    }
    catch (NullPointerException | NumberFormatException | ClassCastException e) {
      throw new RuntimeException("Error parsing int value for field " + objectFieldName + " in " +
        "config JSON file.\n");
    }
  }

  /**
   * This method converts a json object to a long and handles error reporting.
   */
  public long getLong(String objectFieldName) {
    try {
      if(!jsonObjHasKey(jsonObject, objectFieldName)){
        throw new NullPointerException();
      }
      return this.jsonObject.get(objectFieldName).getAsLong();
    }
    catch (NullPointerException | NumberFormatException | ClassCastException e) {
      throw new RuntimeException("Error parsing long value for field " + objectFieldName + " in " +
        "config JSON file.\n");
    }
  }

  /**
   * This method converts a json object to a double and handles error reporting.
   */
  public double getDouble(String objectFieldName) {
    try {
      if(!jsonObjHasKey(jsonObject, objectFieldName)){
        throw new NullPointerException();
      }
      return this.jsonObject.get(objectFieldName).getAsDouble();
    }
    catch (NullPointerException | NumberFormatException | ClassCastException e) {
      throw new RuntimeException("Error parsing double value for field " + objectFieldName + " in " +
        "config JSON file.\n");
    }
  }

  /**
   * This method converts a json array to a list of strings and handles error reporting.
   */
  public List<String> getStringList(String arrayFieldName) {
    JsonArray jsonArray = this.getJSONArray(arrayFieldName);
    List<String> stringList = new ArrayList<>();

    // loop through and add to the array
    for (JsonElement jsonStringObj : jsonArray){
      String string = jsonStringObj.getAsString();

      // if not found or assigned to null, give error
      if (string == null) {
        throw new RuntimeException("Error parsing string from jsonArray " + arrayFieldName +
          ". String value is null or not found.\n");
      }
      stringList.add(string);
    }

    return stringList;
  }

  /**
   * This method converts a json array to a list of strings which represent paths to files,
   * and checks that the files exists. If one doesn't then an error is thrown.
   * The prependToPathString variable is added to the front of the path strings.
   */
  public List<String> getPathStringList(String arrayFieldName) {

    List<String> stringList = this.getStringList(arrayFieldName);
    List<String> pathStringList = new ArrayList<>();

    // loop through the string list and check that each file exists
    // if it does then add the full path to the file to the pathStringList
    for (String pathString : stringList){
      if (fileMissing(getAbsolutePath(pathString))) {
        throw new RuntimeException("Error parsing config JSON. The file " + getAbsolutePath(pathString)
          + " in array " + arrayFieldName + " does not exist or is not readable.\n");
      }
      pathStringList.add(getAbsolutePath(pathString));
    }

    return pathStringList;
  }

  /**
   * This method converts a json array to a list of durations and handles error reporting.
   */
  public List<Duration> getDurationList(String arrayFieldName) {
    JsonArray jsonArray = this.getJSONArray(arrayFieldName);
    List<Duration> durationList = new ArrayList<>();

    // loop through and add to the array
    for (JsonElement jsonStringObj : jsonArray){
      // attempt to add the duration, otherwise throw error
      try {
        durationList.add(new Duration(jsonStringObj.getAsString()));
      }
      catch (RuntimeException e) {
        throw new RuntimeException("Error parsing duration for field " + arrayFieldName + ". Check " +
          "that the duration string is defined and not malformed.\n\n" + e.toString());
      }
    }

    return durationList;
  }

  /**
   * This method converts a json array to a list of times and handles error reporting.
   */
  public List<Time> getTimeList(String arrayFieldName) {
    JsonArray jsonArray = this.getJSONArray(arrayFieldName);
    List<Time> timeList = new ArrayList<>();

    // loop through and add to the array
    for (JsonElement jsonStringObj : jsonArray){
      // attempt to add the time, otherwise throw error
      try {
        timeList.add(EpochRelativeTime.getAbsoluteOrRelativeTime(jsonStringObj.getAsString()));
      }
      catch (RuntimeException e) {
        throw new RuntimeException("Error parsing time for field " + arrayFieldName + ". Check " +
          "that the duration string is defined and not malformed.\n\n" + e.toString());
      }
    }

    return timeList;
  }

  /**
   * This method is called on a ConfigObject and returns a window. Assumes the object contains "start"/"begin" and "end" fields.
   */
  public Window getWindow(){
    try{
      return new Window(jsonObjHasKey(this.jsonObject, "start") ? this.getTime("start") : this.getTime("begin"), this.getTime("end"));
    }
    catch (RuntimeException e){
      throw new RuntimeException(String.format("Window objects must contain \"start\" and \"end\" fields.\n%s", e));
    }
  }

  /**
   * This method is called on a ConfigObject and returns a list of windows. Assumes each object in the list contains "start" and "end" fields.
   */
  public List<Window> getWindowList(String arrayFieldName){
    List<Window> windows = new ArrayList<>();

    JsonArray jsonArray = this.getJSONArray(arrayFieldName);

    for (JsonElement object : jsonArray){
      ConfigObject configObject = new ConfigObject(object.getAsJsonObject());
      windows.add(configObject.getWindow());
    }

    return windows;
  }

  /**
   * This method returns a Set of keys that the ConfigObject can be queried with without errors
   */
  public Set<String> keySet(){
    return jsonObject.keySet();
  }

  /**
   * Returns true if key exists in JSON object
   * @param obj
   * @param key
   * @return
   */
  public static boolean jsonObjHasKey(JsonObject obj, String key){
    return obj.get(key) != null && !obj.get(key).isJsonNull();
  }

}
