package ru.app.command;

import java.sql.SQLException;
import ru.app.collection.Collection;
import ru.app.db.DatabaseManager;
import ru.app.network.Request;

/**
 * Command to register a new user.
 *
 * <p>This command does not require authentication — users must be able to register before logging
 * in. Extracts {@code login} and {@code password} from the {@link Request} and persists the user in
 * the database with SHA-1 hashed password.
 *
 * <p>Returns an error if the user already exists or if the credentials are missing.
 */
public class RegisterCommand extends AbstractCommand {
  private final DatabaseManager dbManager;

  /**
   * Creates a new RegisterCommand.
   *
   * @param dbManager the database manager for persisting the user
   */
  public RegisterCommand(DatabaseManager dbManager) {
    this.dbManager = dbManager;
  }

  @Override
  public String getName() {
    return "register";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    String login = request.login();
    String password = request.password();

    if (login == null || login.isEmpty())
      return CommandResult.error("Login can't be null or empty.");
    if (password == null || password.isEmpty())
      return CommandResult.error("Password can't be null or empty.");

    try {
      dbManager.registerUser(login, password);
      return CommandResult.success("User '" + login + "' registered successfully.");
    } catch (SQLException e) {
      String msg = e.getMessage();
      if (msg != null && msg.contains("duplicate"))
        return CommandResult.error("User '" + login + "' already exists.");
      return CommandResult.error("Failed to register user: " + e.getMessage());
    }
  }
}
