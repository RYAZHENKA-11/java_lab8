package ru.app.command;

import ru.app.collection.Collection;
import ru.app.network.Request;

/**
 * Command to display the history of executed commands.
 *
 * <p>This command is not implemented in server mode. Command history tracking is available only on
 * the client side.
 */
public class HistoryCommand extends AbstractCommand {

  @Override
  public String getName() {
    return "history";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    return CommandResult.success("History not implemented in server mode");
  }
}
