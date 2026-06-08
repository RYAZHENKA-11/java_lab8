package ru.app.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.app.collection.Collection;
import ru.app.command.AbstractCommand;
import ru.app.command.CommandResult;
import ru.app.db.DatabaseManager;
import ru.app.network.CommandType;
import ru.app.network.Request;
import ru.app.network.Response;

/**
 * Server command module - processes commands and manages collection.
 *
 * <p>Uses Command pattern to execute commands on the collection. Converts CommandResult to Response
 * for sending back to client. Modification commands are executed within database transactions
 * managed by {@link DatabaseManager}.
 *
 * <p>Before executing any command (except {@link CommandType#REGISTER}), the module verifies the
 * user's credentials via {@link DatabaseManager#authenticateUser(String, String)}.
 */
public class ServerCommandModule {
  private static final Logger logger = LogManager.getLogger(ServerCommandModule.class);
  private final Collection collection;
  private final CommandFactory commandFactory;
  private final DatabaseManager dbManager;

  /**
   * Creates a new command module.
   *
   * @param collection collection to manage
   * @param dbManager database manager for persisting modifications
   */
  public ServerCommandModule(Collection collection, DatabaseManager dbManager) {
    this.collection = collection;
    this.dbManager = dbManager;
    this.commandFactory = new CommandFactory();
  }

  /**
   * Executes a request and returns response.
   *
   * <p>For {@link CommandType#REGISTER}, authentication is not required. For all other commands,
   * the request must contain valid {@code login} and {@code password}, which are verified against
   * the database.
   *
   * <p>Thread safety is ensured by synchronizing all public methods in {@link DatabaseManager} and
   * {@link ru.app.collection.Collection}.
   *
   * @param request client request
   * @return response to send back to client
   */
  public Response execute(Request request) {
    if (request == null || request.type() == null) return Response.error("Invalid request");

    if (request.type() != CommandType.REGISTER) {
      if (request.login() == null || request.password() == null)
        return Response.error("Authentication required. Please login first.");
      try {
        boolean authenticated = dbManager.authenticateUser(request.login(), request.password());
        if (!authenticated) return Response.error("Invalid login or password.");
      } catch (Exception e) {
        logger.error("Authentication error: {}", e.getMessage(), e);
        return Response.error("Authentication failed: " + e.getMessage());
      }
    }

    AbstractCommand command = commandFactory.create(request.type(), dbManager);
    CommandResult result = command.execute(request, collection);
    return convertToResponse(result);
  }

  /**
   * Converts CommandResult to Response.
   *
   * @param result command execution result
   * @return response object
   */
  private Response convertToResponse(CommandResult result) {
    if (result == null) return Response.error("No result");
    if (result.success()) {
      if (result.data() != null) return Response.successData(result.message(), result.data());
      return Response.success(result.message());
    }
    return Response.error(result.message());
  }
}
