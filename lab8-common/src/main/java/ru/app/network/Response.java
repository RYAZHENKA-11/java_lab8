package ru.app.network;

import java.io.Serializable;
import java.util.List;

/**
 * Response object sent from server to client over UDP.
 *
 * <p>Contains the execution result of a command, including success status, a descriptive message,
 * and optional data (e.g., a list of products). This record is serialized using Java serialization
 * before being transmitted over the network.
 *
 * @param success whether the command executed successfully
 * @param message descriptive message (success message or error description)
 * @param data optional list of result data (e.g., products for {@code show} command)
 */
public record Response(boolean success, String message, List<?> data) implements Serializable {

  /**
   * Creates an error response with the given message.
   *
   * @param message error description
   * @return error response
   */
  public static Response error(String message) {
    return new Response(false, message, null);
  }

  /**
   * Creates a successful response with the given message.
   *
   * @param message success description
   * @return success response
   */
  public static Response success(String message) {
    return new Response(true, message, null);
  }

  /**
   * Creates a successful response with message and data.
   *
   * @param message success description
   * @param data list of result data to include in the response
   * @return success response with data
   */
  public static Response successData(String message, List<?> data) {
    return new Response(true, message, data);
  }
}
