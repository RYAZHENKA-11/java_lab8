package ru.app.command;

import ru.app.collection.Collection;
import ru.app.network.Request;

/**
 * Command that validates user credentials.
 *
 * <p>This command is invoked after {@link ru.app.server.ServerCommandModule} has already
 * authenticated the user via {@link ru.app.db.DatabaseManager#authenticateUser(String, String)}. If
 * authentication failed, the command module returns an error before this command is ever executed.
 * Therefore, this command simply returns a success response, confirming that the provided login and
 * password form a valid registered account.
 */
public class LoginCommand extends AbstractCommand {

  @Override
  public String getName() {
    return "login";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    return CommandResult.success("Authenticated as '" + request.login() + "'.");
  }
}
