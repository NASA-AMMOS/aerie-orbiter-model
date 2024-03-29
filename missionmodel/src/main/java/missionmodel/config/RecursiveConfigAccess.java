package missionmodel.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.Reader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static missionmodel.config.ConfigObject.jsonObjHasKey;

public class RecursiveConfigAccess {

  /**
   * Allows return of any arbitrary JSON result when given path through JSON
   * @return Object for method that calls this to cast, can be any form of JSON result
   */
  public static JsonElement getArbitraryJSON(JsonObject toLookup, List<String> indices){
    JsonElement currentLevel = toLookup;
    for(String index : indices){
      if(jsonObjHasKey(currentLevel.getAsJsonObject(), index)){
        currentLevel = currentLevel.getAsJsonObject().get(index);
      }
      else{
        return null;
      }
    }

    return currentLevel;
  }

  /**
   * Wraps anachronistically-named recursivelyUpdateGsonTypes with slightly easier-to-use call
   */
  public static Object getJavaDataStructsFromJSON(Reader reader){
    Gson gson = new Gson();
    return recursivelyUpdateGsonTypes(gson.fromJson(reader, JsonElement.class));
  }

  /**
   * This code produces an Object, which can be a Map, List, or Java wrapper type - basically stripping the 'Json'
   * off everything so you can call .get() after casting to actual parameterized type
   * @param gsonConvertedJson
   * @return
   */
  public static Object recursivelyUpdateGsonTypes(JsonElement gsonConvertedJson) {
    if(gsonConvertedJson.isJsonNull()) {
      return null;
    }
    else if(gsonConvertedJson.isJsonPrimitive()) {
      return handlePrimitive(gsonConvertedJson.getAsJsonPrimitive());
    }
    else if(gsonConvertedJson.isJsonArray()) {
      // call recursivelyUpdateGsonType on each element of the array
      return StreamSupport.stream(gsonConvertedJson.getAsJsonArray().spliterator(), true).map(RecursiveConfigAccess::recursivelyUpdateGsonTypes).collect(Collectors.toList());
    }
    else {
      // call recursivelyUpdateGsonType on each element of the map
      return StreamSupport.stream(gsonConvertedJson.getAsJsonObject().entrySet().spliterator(), true).filter(e->!e.getValue().isJsonNull()).collect(Collectors.toMap(Map.Entry::getKey, e->recursivelyUpdateGsonTypes(e.getValue())));
    }
  }

  /**
   * Handles primitive types instead of using bigint/bigdecimal.
   * This code is adapted from https://stackoverflow.com/questions/2779251/how-can-i-convert-json-to-a-hashmap-using-gson
   * @param json
   * @return
   */
  private static Object handlePrimitive(JsonPrimitive json) {
    if(json.isBoolean())
      return json.getAsBoolean();
    else if(json.isString())
      return json.getAsString();
    else {
      BigDecimal bigDec = json.getAsBigDecimal();
      // Find out if it is an int type
      try {
        bigDec.toBigIntegerExact();
        try { return bigDec.intValueExact(); }
        catch(ArithmeticException e) {}
        return bigDec.longValue();
      } catch(ArithmeticException e) {}
      // Just return it as a double
      return bigDec.doubleValue();
    }
  }
}
