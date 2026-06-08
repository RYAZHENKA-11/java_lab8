package ru.app.command;

import ru.app.collection.Collection;
import ru.app.network.Request;

/**
 * Command to save the collection.
 *
 * <p>Since the collection is now persisted to the database automatically on every modification,
 * this command no longer performs any action. It returns a success message indicating that the
 * collection is always in sync with the database.
 */
public class SaveCommand extends AbstractCommand {

  @Override
  public String getName() {
    return "save";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    return CommandResult.success("Collection is automatically persisted to database");
  }
}
