/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.annotation.DoNotInherit;
import akka.util.ByteString;

/** Not for user extension, instances provided by {@link HttpClientProvider} and the testkit. */
@DoNotInherit
public interface HttpClient {
  RequestBuilder<ByteString> GET(String uri);

  RequestBuilder<ByteString> POST(String uri);

  RequestBuilder<ByteString> PUT(String uri);

  RequestBuilder<ByteString> PATCH(String uri);

  RequestBuilder<ByteString> DELETE(String uri);
}
