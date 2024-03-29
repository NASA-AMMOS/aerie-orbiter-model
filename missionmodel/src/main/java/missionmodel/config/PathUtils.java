package missionmodel.config;

import java.io.File;

public class PathUtils {
  /**
   * Checks if a file exists. Returns true if it does and false if not.
   */
  public static boolean fileMissing(String pathString) {
    File tempFile = new File(pathString);
    return !tempFile.exists();
  }

  /**
   * Returns whether an input path is relative by looking for / at the start of the path.
   * True = relative, false = absolute.
   */
  public static boolean pathIsRelative(String pathString) {
    File tempFile = new File(pathString);
    return !tempFile.isAbsolute();
  }

  /**
   * Returns an absolute path given an absolute or relative path. Converts relative
   * paths to absolute by using the current working directory.
   */
  public static String getAbsolutePath(String pathString) {
    return getAbsolutePath(System.getProperty("user.dir"), pathString);
  }

  /**
   * Returns an absolute path given an absolute or relative path. Converts relative
   * paths to absolute by using the first path parameter
   */
  public static String getAbsolutePath(String directory, String pathString){
    if(pathIsRelative(pathString)) {
      return directory + File.separator + pathString;
    }
    return pathString;
  }
}
