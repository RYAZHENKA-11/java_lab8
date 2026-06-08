package ru.app.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import ru.app.network.Request;

/**
 * Server request module — deserializes incoming requests from a {@link ByteBuffer}.
 *
 * <p>Converts serialized bytes back into {@link Request} objects using Java serialization. If
 * deserialization fails for any reason, {@code null} is returned.
 */
public class ServerRequestModule {

  /**
   * Reads and deserializes a {@link Request} from the given buffer.
   *
   * @param buffer byte buffer containing the serialized request data
   * @return the deserialized {@link Request} object, or {@code null} if deserialization fails due
   *     to invalid data, class not found, or I/O errors
   */
  public Request read(ByteBuffer buffer) {
    try {
      byte[] data = new byte[buffer.remaining()];
      buffer.get(data);

      ByteArrayInputStream bais = new ByteArrayInputStream(data);
      ObjectInputStream ois = new ObjectInputStream(bais);

      Object obj = ois.readObject();
      ois.close();

      if (obj instanceof Request request) return request;
      return null;
    } catch (IOException | ClassNotFoundException e) {
      return null;
    }
  }
}
