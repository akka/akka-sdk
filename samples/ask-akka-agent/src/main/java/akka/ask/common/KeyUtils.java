package akka.ask.common;

public class KeyUtils {

  public static String readMongoDbUri() {
    return System.getenv("MONGODB_ATLAS_URI");
  }

  public static String readOpenAiKey() {
    return System.getenv("OPENAI_API_KEY");
  }

  public static boolean hasValidKeys() {
    try {
      return !readMongoDbUri().isEmpty() && !readOpenAiKey().isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

}
