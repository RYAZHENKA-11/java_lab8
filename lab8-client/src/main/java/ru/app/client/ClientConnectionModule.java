package ru.app.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import ru.app.network.Request;
import ru.app.network.Response;

/**
 * Client connection module — handles UDP communication with the server.
 *
 * <p>Sends serialized {@link Request} objects to the server and receives {@link Response} objects
 * back. Includes retry logic for handling temporary server unavailability.
 */
public class ClientConnectionModule {
  private final InetSocketAddress serverAddress;
  private final int maxRetries;
  private final DatagramSocket socket;

  /**
   * Creates a new client connection module and opens a UDP socket.
   *
   * @param host server hostname
   * @param port server port
   * @param timeoutMs read timeout in milliseconds for each retry attempt
   * @param maxRetries maximum number of retry attempts before giving up
   * @throws Exception if the socket cannot be created or configured
   */
  public ClientConnectionModule(String host, int port, int timeoutMs, int maxRetries)
      throws Exception {
    this.serverAddress = new InetSocketAddress(host, port);
    this.maxRetries = maxRetries;
    this.socket = new DatagramSocket();
    this.socket.setSoTimeout(timeoutMs);
  }

  /**
   * Sends a request to the server and waits for a response, retrying on timeout.
   *
   * @param request the request to send
   * @return the server response, or an error response if all retries fail
   */
  public Response sendRequest(Request request) {
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        byte[] requestData = serializeRequest(request);
        DatagramPacket requestPacket =
            new DatagramPacket(requestData, requestData.length, serverAddress);
        socket.send(requestPacket);

        byte[] responseBuffer = new byte[65535];
        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
        socket.receive(responsePacket);

        return deserializeResponse(responseBuffer, responsePacket.getLength());

      } catch (IOException e) {
        if (attempt == maxRetries) return Response.error("Server unavailable: " + e.getMessage());
      }
    }
    return Response.error("Server unavailable after " + maxRetries + " attempts");
  }

  /**
   * Serializes a request to a byte array using Java serialization.
   *
   * @param request the request to serialize
   * @return the serialized byte array
   * @throws IOException if serialization fails
   */
  private byte[] serializeRequest(Request request) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(request);
    oos.close();
    return baos.toByteArray();
  }

  /**
   * Deserializes a response from a byte array using Java serialization.
   *
   * @param data the byte array containing the serialized response
   * @param length the number of valid bytes in the array
   * @return the deserialized {@link Response}, or an error response if deserialization fails
   * @throws IOException if the byte stream cannot be read
   */
  private Response deserializeResponse(byte[] data, int length) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(data, 0, length);
    ObjectInputStream ois = new ObjectInputStream(bais);
    try {
      Object obj = ois.readObject();
      if (obj instanceof Response response) return response;
      return Response.error("Invalid response type");
    } catch (ClassNotFoundException e) {
      return Response.error("Invalid response format");
    }
  }

  /** Closes the client UDP socket. */
  public void close() {
    if (socket != null && !socket.isClosed()) socket.close();
  }
}
