package missionmodel.geometry.interfaces;

@FunctionalInterface
public interface FunctionWithGeometricException<T, R> {
  /**
   * A function used to pass into getWindowsWhenConditionMet in SpiceDirectEventGenerator
   * @param t
   * @return
   * @throws GeometryInformationNotAvailableException if the function fails to compute values at a point
   */
  R apply(T t) throws GeometryInformationNotAvailableException;
}
