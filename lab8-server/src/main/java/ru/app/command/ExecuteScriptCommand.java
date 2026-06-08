package ru.app.command;

import ru.app.collection.Collection;
import ru.app.network.Request;

/**
 * Command to execute commands from a script file.
 *
 * <p>This command is not supported via network requests. Script execution should be initiated from
 * the client side using the {@code execute_script} command.
 */
public class ExecuteScriptCommand extends AbstractCommand {

  @Override
  public String getName() {
    return "execute_script";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    return CommandResult.error(
        "execute_script not supported via network - use client-side execution");
  }
}
