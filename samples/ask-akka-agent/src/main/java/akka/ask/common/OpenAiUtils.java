package akka.ask.common;

import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModelName;

public class OpenAiUtils {

  final private static OpenAiEmbeddingModelName embeddingModelName = OpenAiEmbeddingModelName.TEXT_EMBEDDING_3_SMALL;

  public static OpenAiEmbeddingModel embeddingModel() {
    return OpenAiEmbeddingModel.builder()
      .apiKey(KeyUtils.readOpenAiKey())
      .modelName(embeddingModelName)
      .build();
  }

}
