package ru.app.command;

import java.sql.SQLException;
import ru.app.collection.Collection;
import ru.app.db.DatabaseManager;
import ru.app.network.Request;

/**
 * Command to clear all elements from the collection that belong to the current user.
 *
 * <p>Only products created by the requesting user are deleted from the database and removed from
 * the in-memory collection. If the database operation fails, the transaction is rolled back.
 */
public class ClearCommand extends AbstractCommand {
  private final DatabaseManager dbManager;

  /**
   * Creates a new ClearCommand.
   *
   * @param dbManager the database manager for persisting the clear operation
   */
  public ClearCommand(DatabaseManager dbManager) {
    this.dbManager = dbManager;
  }

  @Override
  public String getName() {
    return "clear";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    try {
      dbManager.beginTransaction();
      try {
        collection.removeByUser(request.login());
      } catch (IllegalArgumentException e) {
        dbManager.rollback();
        return CommandResult.error("Failed to clear collection: " + e.getMessage());
      }
      dbManager.clearUserProducts(request.login());
      dbManager.commit();
      return CommandResult.success("Your products were cleared.");
    } catch (SQLException e) {
      dbManager.rollback();
      return CommandResult.error("Failed to clear collection: " + e.getMessage());
    }
  }
}
