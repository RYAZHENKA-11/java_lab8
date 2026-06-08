package ru.app.command;

import java.sql.SQLException;
import ru.app.collection.Collection;
import ru.app.db.DatabaseManager;
import ru.app.network.Request;
import ru.app.object.Product;

/**
 * Command to remove the first element from the collection.
 *
 * <p>The first element is determined by the smallest {@code id} in the priority queue. The product
 * is first deleted from the database, then removed from the in-memory collection. If either step
 * fails, the database transaction is rolled back.
 *
 * <p>The user can only remove products that they own (where {@code createdBy} matches their login).
 */
public class RemoveFirstCommand extends AbstractCommand {
  private final DatabaseManager dbManager;

  /**
   * Creates a new RemoveFirstCommand.
   *
   * @param dbManager the database manager for persisting the deletion
   */
  public RemoveFirstCommand(DatabaseManager dbManager) {
    this.dbManager = dbManager;
  }

  @Override
  public String getName() {
    return "remove_first";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    try {
      dbManager.beginTransaction();

      // Atomically get and remove the first element
      Product first = collection.pollFirst();
      if (first == null) {
        dbManager.rollback();
        return CommandResult.error("Collection is empty.");
      }

      Integer firstId = first.id();
      String owner = dbManager.getProductOwner(firstId);
      if (owner == null) {
        // Put the product back since it wasn't in DB
        collection.add(first);
        dbManager.rollback();
        return CommandResult.error("First product not found in database.");
      }
      if (!request.login().equals(owner)) {
        // Put the product back since user doesn't own it
        collection.add(first);
        dbManager.rollback();
        return CommandResult.error("You can only modify your own products.");
      }

      if (!dbManager.deleteProduct(firstId)) {
        // Put the product back
        collection.add(first);
        dbManager.rollback();
        return CommandResult.error("First product not found in database.");
      }

      dbManager.commit();
      return CommandResult.success("First product removed");
    } catch (SQLException e) {
      dbManager.rollback();
      return CommandResult.error("Failed to remove product: " + e.getMessage());
    }
  }
}
