package ru.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.app.collection.Collection;
import ru.app.db.DatabaseManager;
import ru.app.db.DbConfig;
import ru.app.object.Product;
import ru.app.server.ServerConnectionModule;

/**
 * Server entry point.
 *
 * <p>Initializes the database connection, loads the collection from PostgreSQL, and starts the UDP
 * server loop. The collection is stored in memory and all modifications are automatically persisted
 * to the database.
 *
 * <p>Command-line arguments (optional):
 *
 * <ol>
 *   <li>JDBC URL (default: {@code jdbc:postgresql://localhost:5432/studs})
 *   <li>Database user (default: {@code s501768})
 *   <li>Database password (default: {@code SSVe/3232})
 * </ol>
 */
public class Server {
  private static final Logger logger = LogManager.getLogger(Server.class);
  private static final int PORT = 8888;
  private static final int BUFFER_SIZE = 65535;

  /**
   * Server main method.
   *
   * @param args command-line arguments for database connection (url, user, password)
   */
  public static void main(String[] args) {
    logger.info("Server starting on port {}", PORT);
    DbConfig dbConfig = extractDbConfig(args);
    DatabaseManager dbManager = new DatabaseManager(dbConfig);

    try {
      dbManager.connect();
      dbManager.initSchema();
    } catch (Exception e) {
      logger.error("Failed to connect to database: {}", e.getMessage(), e);
      return;
    }

    Collection collection = initializeCollectionFromDb(dbManager);
    logger.info("Server started on port {}", PORT);
    logger.info("Collection loaded from DB: {} elements", collection.size());

    try {
      ServerConnectionModule server =
          new ServerConnectionModule(PORT, collection, BUFFER_SIZE, dbManager);
      startServerConsole(dbManager, server);
      server.start();
    } catch (IOException e) {
      logger.error("Server error: {}", e.getMessage(), e);
    } finally {
      dbManager.disconnect();
    }
  }

  /**
   * Extracts database connection parameters from command-line arguments. Falls back to default
   * values if fewer than 3 arguments are provided.
   *
   * @param args command-line arguments
   * @return database configuration record
   */
  private static DbConfig extractDbConfig(String[] args) {
    String url;
    String user;
    String password;

    if (args != null && args.length >= 3) {
      url = args[0];
      user = args[1];
      password = args[2];
    } else {
      logger.info("No DB credentials provided, using defaults");
      url = "jdbc:postgresql://localhost:5432/studs";
      user = "s501768";
      password = "SSVe";
    }

    return new DbConfig(url, user, password);
  }

  /**
   * Loads all products from the database and populates the in-memory collection. Products that fail
   * uniqueness validation are skipped with a warning.
   *
   * @param dbManager the database manager to load products from
   * @return initialized collection, or empty collection if loading fails
   */
  private static Collection initializeCollectionFromDb(DatabaseManager dbManager) {
    try {
      var products = dbManager.loadAll();
      Collection collection = new Collection();
      for (Product p : products) {
        try {
          collection.add(p);
        } catch (IllegalArgumentException e) {
          logger.warn("Skipped product id={}: {}", p.id(), e.getMessage());
        }
      }
      return collection;
    } catch (Exception e) {
      logger.error(
          "Failed to load collection from DB: {}. Starting with empty collection.", e.getMessage());
      return new Collection();
    }
  }

  /**
   * Starts a thread that reads commands from the server console. Supported commands: {@code exit} —
   * gracefully shuts down the server (thread pools, network channel, selector) and disconnects from
   * the database.
   *
   * @param dbManager the database manager to disconnect on shutdown
   * @param server the server module to shut down on exit
   */
  private static void startServerConsole(DatabaseManager dbManager, ServerConnectionModule server) {
    Thread consoleThread =
        new Thread(
            () -> {
              BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
              System.out.println("Server console available. Type 'exit' to stop.");
              while (true) {
                try {
                  String line = console.readLine();
                  if (line == null) break;
                  line = line.trim();
                  if (line.equals("exit")) {
                    logger.info("Shutting down server...");
                    try {
                      server.shutdown();
                    } catch (IOException e) {
                      logger.error("Shutdown error: {}", e.getMessage(), e);
                    }
                    dbManager.disconnect();
                    break;
                  }
                } catch (IOException e) {
                  break;
                }
              }
            });
    consoleThread.start();
  }
}
