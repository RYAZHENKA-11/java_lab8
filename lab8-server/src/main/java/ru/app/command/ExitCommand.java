package ru.app.command;

import ru.app.collection.Collection;
import ru.app.network.Request;

/**
 * Command to exit the application.
 *
 * <p>This command is not available via network requests. The server should be stopped using the
 * server console {@code exit} command.
 */
public class ExitCommand extends AbstractCommand {

  @Override
  public String getName() {
    return "exit";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    return CommandResult.error("exit command is not available via network");
  }
}
