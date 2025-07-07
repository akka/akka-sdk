package akka.ask.common;

import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyUtils {

  private static final Logger logger = LoggerFactory.getLogger(KeyUtils.class);

  public static String readMongoDbUri() {
    return System.getenv("MONGODB_ATLAS_URI");
  }

  public static String readOpenAiKey() {
    return System.getenv("OPENAI_API_KEY");
  }

  public static void checkKeys(Config config) {
    if (config.getString("akka.javasdk.agent.model-provider").equals("openai")
      && readOpenAiKey().isBlank()) {
      throw new IllegalStateException(
        "No API keys found. Make sure you have OPENAI_API_KEY defined as environment variable, or change the model provider configuration in application.conf to use a different LLM.");
    }
    if (!hasMongoDbUri()) {
      throw new IllegalStateException(
        "No API key found. Make sure you have MONGODB_ATLAS_URI defined as environment variable.");
    }
  }

  public static boolean hasMongoDbUri() {
    try {
      return !readMongoDbUri().isEmpty();
    } catch (Exception e) {
      return false;
    }
  }
}
