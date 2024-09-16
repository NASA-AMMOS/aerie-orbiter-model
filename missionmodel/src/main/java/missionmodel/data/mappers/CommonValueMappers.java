package missionmodel.data.mappers;

import gov.nasa.jpl.aerie.merlin.framework.Result;
import gov.nasa.jpl.aerie.merlin.framework.ValueMapper;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.merlin.protocol.types.ValueSchema;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public class CommonValueMappers {

  public static ValueMapper<Instant> instant() {
    return new InstantValueMapper();
  }

  public static <T> ValueMapper<Optional<T>> optional(ValueMapper<T> valueMapper) {
    return new ValueMapper<>() {
        @Override
        public ValueSchema getValueSchema() {
            return ValueSchema.ofStruct(Map.of("present", ValueSchema.BOOLEAN, "value", valueMapper.getValueSchema()));
        }

        @Override
        public Result<Optional<T>, String> deserializeValue(SerializedValue serializedValue) {
          if (serializedValue.asMap().isEmpty()) return Result.failure("");
          SerializedValue present = serializedValue.asMap().get().get("present");
          if (present == null) return Result.failure("missing field 'present'");

          if (present.equals(SerializedValue.of(false))) return Result.success(Optional.empty());

          SerializedValue value = serializedValue.asMap().get().get("value");
          if (value == null) return Result.failure("missing field 'value'");

          return valueMapper.deserializeValue(value).mapSuccess(Optional::of);
        }

        @Override
        public SerializedValue serializeValue(Optional<T> t) {
            return SerializedValue.of(Map.of(
                    "present", SerializedValue.of(t.isPresent()),
                    "value", t.map(valueMapper::serializeValue).orElse(SerializedValue.NULL)));
        }
    };
  }
}
