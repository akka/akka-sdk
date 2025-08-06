
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
  @JsonSubTypes.Type(value = Result.Succeed.class),
  @JsonSubTypes.Type(value = Result.Failed.class)
})
public interface Result {
  record Failed(String errorMsg) implements Result {}

  record Succeed() implements Result {}
}
