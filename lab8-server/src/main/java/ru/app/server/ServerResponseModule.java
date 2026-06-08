package ru.app.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import ru.app.network.Response;

/**
 * Server response module — serializes and sends responses to clients via UDP.
 *
 * <p>Converts {@link Response} objects to bytes using Java serialization and transmits them through
 * the provided {@link DatagramChannel}.
 */
public class ServerResponseModule {
  private final DatagramChannel channel;

  /**
   * Creates a new response module.
   *
   * @param channel the datagram channel used for sending responses
   */
  public ServerResponseModule(DatagramChannel channel) {
    this.channel = channel;
  }

  /**
   * Serializes the response and sends it to the specified client address.
   *
   * @param response the response to send
   * @param clientAddress the target client address
   * @throws IOException if serialization fails or the datagram cannot be sent
   */
  public void send(Response response, InetSocketAddress clientAddress) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(response);
    oos.close();

    byte[] data = baos.toByteArray();
    ByteBuffer buffer = ByteBuffer.wrap(data);
    channel.send(buffer, clientAddress);
  }
}
