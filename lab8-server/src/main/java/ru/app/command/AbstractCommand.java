package ru.app.command;

/**
 * Abstract base class for all commands.
 *
 * <p>Provides common functionality for command implementations.
 */
public abstract class AbstractCommand implements Command {

  /**
   * Returns the command name.
   *
   * @return command name
   */
  public abstract String getName();
}
