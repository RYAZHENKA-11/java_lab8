package ru.app.command;

import java.util.List;

/**
 * Result of command execution.
 *
 * <p>Contains success status, message, and optional data.
 */
public record CommandResult(boolean success, String message, List<?> data) {

  /**
   * Creates a successful result with message.
   *
   * @param message success message
   * @return successful result
   */
  public static CommandResult success(String message) {
    return new CommandResult(true, message, null);
  }

  /**
   * Creates a successful result with message and data.
   *
   * @param message success message
   * @param data result data
   * @return successful result with data
   */
  public static CommandResult success(String message, List<?> data) {
    return new CommandResult(true, message, data);
  }

  /**
   * Creates an error result.
   *
   * @param message error message
   * @return error result
   */
  public static CommandResult error(String message) {
    return new CommandResult(false, message, null);
  }
}
