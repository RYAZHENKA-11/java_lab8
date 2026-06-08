package ru.app.command;

import java.sql.SQLException;
import ru.app.collection.Collection;
import ru.app.db.DatabaseManager;
import ru.app.network.Request;
import ru.app.object.Product;

/**
 * Command to update an existing element in the collection by its ID.
 *
 * <p>The product is first updated in the database, then the in-memory collection is updated. If
 * either step fails, the database transaction is rolled back.
 *
 * <p>The user can only update products that they own (where {@code createdBy} matches their login).
 */
public class UpdateCommand extends AbstractCommand {
  private final DatabaseManager dbManager;

  /**
   * Creates a new UpdateCommand.
   *
   * @param dbManager the database manager for persisting the update
   */
  public UpdateCommand(DatabaseManager dbManager) {
    this.dbManager = dbManager;
  }

  @Override
  public String getName() {
    return "update";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    Integer id = request.id();
    Product product = request.product();
    if (id == null) return CommandResult.error("No ID provided");
    if (product == null) return CommandResult.error("No product data provided");

    try {
      dbManager.beginTransaction();

      String owner = dbManager.getProductOwner(id);
      if (owner == null) {
        dbManager.rollback();
        return CommandResult.error("Product with id=" + id + " doesn't exist.");
      }
      if (!request.login().equals(owner)) {
        dbManager.rollback();
        return CommandResult.error("You can only modify your own products.");
      }

      if (!dbManager.updateProduct(id, product)) {
        dbManager.rollback();
        return CommandResult.error("Product with id=" + id + " not found in database.");
      }
      try {
        collection.update(id, product);
      } catch (IllegalArgumentException e) {
        dbManager.rollback();
        return CommandResult.error("Failed to update product: " + e.getMessage());
      }
      dbManager.commit();
      return CommandResult.success("Product with id=" + id + " updated.");
    } catch (SQLException e) {
      dbManager.rollback();
      return CommandResult.error("Failed to update product: " + e.getMessage());
    } catch (IllegalArgumentException e) {
      dbManager.rollback();
      return CommandResult.error(e.getMessage());
    }
  }
}
