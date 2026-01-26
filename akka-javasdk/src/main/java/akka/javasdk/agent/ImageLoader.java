/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.net.URI;
import java.util.Optional;

/**
 * Interface for loading images from URIs.
 *
 * <p>Implement this interface to provide custom image loading logic for multimodal AI agent
 * interactions. This allows loading images from custom sources such as cloud storage, databases, or
 * authenticated endpoints.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class MyImageLoader implements ImageLoader {
 *   @Override
 *   public LoadedImage load(
 *       URI uri,
 *       MessageContent.ImageMessageContent.DetailLevel detailLevel,
 *       Optional<String> mimeType) {
 *     // Load image bytes from your custom source
 *     byte[] imageData = fetchFromStorage(uri);
 *     String actualMimeType = mimeType.orElse("image/jpeg");
 *     return new LoadedImage(imageData, actualMimeType);
 *   }
 * }
 * }</pre>
 *
 * <p>To use the image loader, pass it to the agent effect builder:
 *
 * <pre>{@code
 * return effects()
 *     .imageLoader(new MyImageLoader())
 *     .userMessage(UserMessage.from(
 *         MessageContent.TextMessageContent.from("Describe this image"),
 *         MessageContent.ImageMessageContent.fromUrl(imageUrl)))
 *     .thenReply();
 * }</pre>
 *
 * <p>The instance used could be a new one for each agent request, to for example allow per request
 * credentials, or it could be created globally in the service bootstrap, and made available to each
 * agent via dependency injection.
 *
 * <p>In case of a shared instance, care must be taken that it is thread safe since it can be used
 * by multiple separate agent interactions concurrently.
 *
 * @see MessageContent.ImageMessageContent
 * @see Agent.Effect.Builder#imageLoader(ImageLoader)
 */
public interface ImageLoader {

  /**
   * Represents a loaded image with its binary data and MIME type.
   *
   * @param data The raw image bytes
   * @param mimeType The MIME type of the image (e.g., "image/jpeg", "image/png")
   */
  record LoadedImage(byte[] data, String mimeType) {}

  /**
   * Loads an image from the given URI.
   *
   * <p>This method is called by the runtime when processing multimodal messages that contain image
   * references. The implementation should fetch the image data and return it along with the
   * appropriate MIME type.
   *
   * <p>If the method throws, the entire agent request is failed.
   *
   * @param uri The URI of the image to load
   * @param detailLevel The requested detail level for image processing (LOW, HIGH, or AUTO)
   * @param mimeType Optional MIME type hint provided by the user. Note that the returned mime type
   *     *must* match the actual MIME type of the returned bytes if the input MIME type is not
   *     correct.
   * @return The loaded image data and MIME type
   */
  LoadedImage load(
      URI uri,
      MessageContent.ImageMessageContent.DetailLevel detailLevel,
      Optional<String> mimeType);
}
