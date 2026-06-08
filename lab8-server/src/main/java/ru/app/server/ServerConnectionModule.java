package ru.app.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.app.collection.Collection;
import ru.app.db.DatabaseManager;
import ru.app.network.Request;
import ru.app.network.Response;

/**
 * Server connection module - handles UDP client connections using non-blocking I/O. Acts as the
 * main server loop that accepts requests, processes them, and sends responses.
 *
 * <p>This module integrates:
 *
 * <ul>
 *   <li>{@link ServerRequestModule} - deserializes incoming requests
 *   <li>{@link ServerCommandModule} - executes commands on collection
 *   <li>{@link ServerResponseModule} - serializes and sends responses
 * </ul>
 */
public class ServerConnectionModule {
  private static final Logger logger = LogManager.getLogger(ServerConnectionModule.class);
  private final DatagramChannel channel;
  private final Selector selector;
  private final int bufferSize;

  private final ServerRequestModule requestReader;
  private final ServerCommandModule commandExecutor;
  private final ServerResponseModule responseSender;

  private final ExecutorService readPool;
  private final ForkJoinPool processPool;
  private final ExecutorService sendPool;
  private volatile boolean running;

  /**
   * Creates a new server connection module.
   *
   * @param port UDP port to listen on
   * @param collection collection to manage
   * @param bufferSize buffer size for receiving data
   * @param dbManager database manager for persisting collection changes
   * @throws IOException if server cannot be initialized
   */
  public ServerConnectionModule(
      int port, Collection collection, int bufferSize, DatabaseManager dbManager)
      throws IOException {
    this.bufferSize = bufferSize;

    this.channel = DatagramChannel.open();
    channel.bind(new InetSocketAddress(port));
    channel.configureBlocking(false);

    this.selector = Selector.open();
    channel.register(selector, SelectionKey.OP_READ);

    this.requestReader = new ServerRequestModule();
    this.commandExecutor = new ServerCommandModule(collection, dbManager);
    this.responseSender = new ServerResponseModule(channel);

    this.readPool = Executors.newCachedThreadPool();
    this.processPool = new ForkJoinPool();
    this.sendPool = Executors.newCachedThreadPool();
    this.running = true;
  }

  /**
   * Starts the server event loop. Runs in non-blocking mode using Selector.
   *
   * <p>Request processing is distributed across three thread pools:
   *
   * <ul>
   *   <li>{@link #readPool} (CachedThreadPool) — deserializes incoming requests
   *   <li>{@link #processPool} (ForkJoinPool) — executes commands on the collection
   *   <li>{@link #sendPool} (CachedThreadPool) — sends responses back to clients
   * </ul>
   *
   * @throws IOException if server socket fails
   */
  public void start() throws IOException {
    logger.info("Server listening on port {}", channel.socket().getLocalPort());

    while (running) {
      selector.select();

      Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
      while (keys.hasNext()) {
        SelectionKey key = keys.next();
        keys.remove();
        if (key.isReadable()) processRequest();
      }
    }
  }

  /**
   * Processes a single request from a client using a multithreaded pipeline.
   *
   * <p>Execution flow:
   *
   * <ol>
   *   <li>The UDP packet is received in the selector thread (fast I/O operation).
   *   <li>Deserialization is submitted to {@link #readPool} (CachedThreadPool).
   *   <li>Command execution is submitted to {@link #processPool} (ForkJoinPool).
   *   <li>Response sending is submitted to {@link #sendPool} (CachedThreadPool).
   * </ol>
   */
  private void processRequest() {
    ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
    InetSocketAddress clientAddress;

    try {
      logger.info("Waiting to receive packet...");
      clientAddress = (InetSocketAddress) channel.receive(buffer);
      logger.info("Received packet from: {}", clientAddress);

      if (clientAddress != null) {
        buffer.flip();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        final InetSocketAddress addr = clientAddress;
        readPool.submit(() -> handleRead(data, addr));
      }
    } catch (IOException e) {
      logger.error("Error receiving packet: {}", e.getMessage(), e);
    }
  }

  /**
   * Handles the deserialization of a request in the read pool.
   *
   * @param data raw byte array received from the client
   * @param clientAddress the address of the client that sent the request
   */
  private void handleRead(byte[] data, InetSocketAddress clientAddress) {
    try {
      ByteBuffer buffer = ByteBuffer.wrap(data);
      Request request = requestReader.read(buffer);

      if (request == null) {
        logger.warn("Invalid request format from {}", clientAddress);
        sendPool.submit(
            () -> {
              try {
                responseSender.send(Response.error("Invalid request format"), clientAddress);
              } catch (IOException e) {
                logger.error(
                    "Failed to send error response to {}: {}", clientAddress, e.getMessage(), e);
              }
            });
        return;
      }

      logger.info("Received {} request from {}", request.type(), clientAddress);
      handleProcess(request, clientAddress);
    } catch (Exception e) {
      logger.error("Error reading request from {}: {}", clientAddress, e.getMessage(), e);
      sendPool.submit(
          () -> {
            try {
              responseSender.send(Response.error("Server error: " + e.getMessage()), clientAddress);
            } catch (IOException ex) {
              logger.error(
                  "Failed to send error response to {}: {}", clientAddress, ex.getMessage(), ex);
            }
          });
    }
  }

  /**
   * Handles command execution in the ForkJoinPool.
   *
   * @param request the deserialized client request
   * @param clientAddress the address of the client that sent the request
   */
  private void handleProcess(Request request, InetSocketAddress clientAddress) {
    processPool.submit(
        () -> {
          try {
            Response response = commandExecutor.execute(request);
            sendPool.submit(
                () -> {
                  try {
                    responseSender.send(response, clientAddress);
                    logger.info(
                        "Sent {} response to {}",
                        response.success() ? "success" : "error",
                        clientAddress);
                  } catch (IOException e) {
                    logger.error(
                        "Failed to send response to {}: {}", clientAddress, e.getMessage(), e);
                  }
                });
          } catch (Exception e) {
            logger.error("Error processing request from {}: {}", clientAddress, e.getMessage(), e);
            sendPool.submit(
                () -> {
                  try {
                    responseSender.send(
                        Response.error("Server error: " + e.getMessage()), clientAddress);
                  } catch (IOException ex) {
                    logger.error(
                        "Failed to send error response to {}: {}",
                        clientAddress,
                        ex.getMessage(),
                        ex);
                  }
                });
          }
        });
  }

  /**
   * Initiates a graceful shutdown of the server and all thread pools.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Sets {@code running} to {@code false} and wakes up the selector.
   *   <li>Shuts down {@link #readPool}, {@link #processPool}, and {@link #sendPool}.
   *   <li>Waits up to 5 seconds for pool termination.
   *   <li>Closes the selector and channel.
   * </ol>
   *
   * @throws IOException if an I/O error occurs while closing the channel or selector
   */
  public void shutdown() throws IOException {
    running = false;
    selector.wakeup();

    readPool.shutdown();
    processPool.shutdown();
    sendPool.shutdown();

    try {
      if (!readPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
        logger.warn("Read pool did not terminate in time, forcing shutdown");
        readPool.shutdownNow();
      }
      if (!processPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
        logger.warn("Process pool did not terminate in time, forcing shutdown");
        processPool.shutdownNow();
      }
      if (!sendPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
        logger.warn("Send pool did not terminate in time, forcing shutdown");
        sendPool.shutdownNow();
      }
    } catch (InterruptedException e) {
      logger.warn("Shutdown interrupted, forcing immediate shutdown");
      readPool.shutdownNow();
      processPool.shutdownNow();
      sendPool.shutdownNow();
      Thread.currentThread().interrupt();
    }

    selector.close();
    channel.close();
    logger.info("Server shutdown complete");
  }
}
