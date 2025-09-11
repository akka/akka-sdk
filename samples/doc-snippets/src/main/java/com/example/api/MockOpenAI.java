package com.example.api;

import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.*;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

// tag::class[]
@HttpEndpoint
@Acl(allow = { @Acl.Matcher(service = "*") })
public class MockOpenAI extends AbstractHttpEndpoint {

  private static final long MIN_DELAY_MILLIS = 2000;
  private static final long MAX_DELAY_MILLIS = 3000;
  private static final long DELAY_SPAN = MAX_DELAY_MILLIS - MIN_DELAY_MILLIS;

  private static final HttpResponse staticResponse = HttpResponse.create()
    .withStatus(StatusCodes.OK)
    .withEntity(
      HttpEntities.create(
        ContentTypes.APPLICATION_JSON,
        """
        { "id": "chatcmpl-Byz9msOuInWGiYmFJR8eH7ei2S3d0",
          "object": "chat.completion",
          "created": 1753874466,
          "model": "gpt-4o-mini-2024-07-18",
           "choices": [
           {
             "index": 0,
             "message": {
               "role": "assistant",
               "content": "Some hardcoded result",
               "refusal": null,
               "annotations": []
             },
             "logprobs": null,
             "finish_reason": "stop"
           }],
           "usage": {
             "prompt_tokens": 29,
             "completion_tokens": 264,
             "total_tokens": 293,
             "prompt_tokens_details": {
               "cached_tokens": 0,
               "audio_tokens": 0
             },
             "completion_tokens_details": {
               "reasoning_tokens": 0,
               "audio_tokens": 0,
               "accepted_prediction_tokens": 0,
               "rejected_prediction_tokens": 0
             }
           },
           "service_tier": "default",
           "system_fingerprint": "fp_197a02a720"
        }"""
      )
    )
    .withHeaders(
      Arrays.asList(
        RawHeader.create("x-request-id", "537dc248-255e-49eb-8799-fcc11a8b6cf0"),
        RawHeader.create("x-ratelimit-limit-tokens", "2000000"),
        RawHeader.create("openai-organization", "abc-123123"),
        RawHeader.create("openai-version", "20200-01"),
        RawHeader.create("openai-processing-ms", "5916"),
        RawHeader.create("openai-project", "proj_1234567abcdef")
      )
    );

  @Post("/chat/completions")
  public HttpResponse completion(HttpEntity.Strict ignoredRequestBody) throws Exception {
    var delay = MIN_DELAY_MILLIS + ThreadLocalRandom.current().nextLong(DELAY_SPAN);
    Thread.sleep(delay);
    return staticResponse;
  }
}
// end::class[]
