package ru.app.command;

import java.sql.SQLException;
import ru.app.collection.Collection;
import ru.app.db.DatabaseManager;
import ru.app.network.Request;

/**
 * Command to remove an element from the collection by its ID.
 *
 * <p>The product is first deleted from the database, then removed from the in-memory collection. If
 * either step fails, the database transaction is rolled back.
 *
 * <p>The user can only remove products that they own (where {@code createdBy} matches their login).
 */
public class RemoveByIdCommand extends AbstractCommand {
  private final DatabaseManager dbManager;

  /**
   * Creates a new RemoveByIdCommand.
   *
   * @param dbManager the database manager for persisting the deletion
   */
  public RemoveByIdCommand(DatabaseManager dbManager) {
    this.dbManager = dbManager;
  }

  @Override
  public String getName() {
    return "remove_by_id";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    Integer id = request.id();
    if (id == null) return CommandResult.error("No ID provided");

    try {
      dbManager.beginTransaction();

      String owner = dbManager.getProductOwner(id);
      if (owner == null) {
        dbManager.rollback();
        return CommandResult.error("Product with id " + id + " doesn't exist.");
      }
      if (!request.login().equals(owner)) {
        dbManager.rollback();
        return CommandResult.error("You can only modify your own products.");
      }

      if (!dbManager.deleteProduct(id)) {
        dbManager.rollback();
        return CommandResult.error("Product with id " + id + " not found in database.");
      }
      try {
        collection.remove(id);
      } catch (IllegalArgumentException e) {
        dbManager.rollback();
        return CommandResult.error("Failed to remove product: " + e.getMessage());
      }
      dbManager.commit();
      return CommandResult.success("Product with id " + id + " removed");
    } catch (SQLException e) {
      dbManager.rollback();
      return CommandResult.error("Failed to remove product: " + e.getMessage());
    }
  }
}
