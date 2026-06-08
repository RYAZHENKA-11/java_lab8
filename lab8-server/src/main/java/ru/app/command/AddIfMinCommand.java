package ru.app.command;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import ru.app.collection.Collection;
import ru.app.db.DatabaseManager;
import ru.app.network.Request;
import ru.app.object.Product;

/**
 * Command to add a product if its {@code id} is less than the minimum {@code id} in the collection.
 *
 * <p>The product {@code id} is obtained from the PostgreSQL sequence before the insert. If the
 * collection is not empty and the next sequence value is not less than the minimum existing {@code
 * id}, the product is not added and the sequence value is discarded. The operation is performed
 * within a database transaction.
 */
public class AddIfMinCommand extends AbstractCommand {
  private final DatabaseManager dbManager;

  /**
   * Creates a new AddIfMinCommand.
   *
   * @param dbManager the database manager for persisting the product
   */
  public AddIfMinCommand(DatabaseManager dbManager) {
    this.dbManager = dbManager;
  }

  @Override
  public String getName() {
    return "add_if_min";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    Product product = request.product();
    if (product == null) return CommandResult.error("No product data provided");

    try {
      dbManager.beginTransaction();

      Product productToInsert =
          new Product(
              null,
              product.name(),
              product.coordinates(),
              product.price(),
              product.partNumber(),
              product.unitOfMeasure(),
              product.owner(),
              ZonedDateTime.now(),
              null);

      Integer generatedId = dbManager.insertProduct(productToInsert, request.login());

      Integer minId = collection.getMinId();
      if (minId != null && generatedId >= minId) {
        dbManager.rollback();
        return CommandResult.error("Product was not added (id is not less than minimum).");
      }

      Product productWithId =
          new Product(
              generatedId,
              product.name(),
              product.coordinates(),
              product.price(),
              product.partNumber(),
              product.unitOfMeasure(),
              product.owner(),
              productToInsert.creationDate(),
              request.login());
      try {
        collection.add(productWithId);
      } catch (IllegalArgumentException e) {
        dbManager.rollback();
        return CommandResult.error("Failed to add product: " + e.getMessage());
      }
      dbManager.commit();
      return CommandResult.success("Product was added with id=" + generatedId);
    } catch (SQLException e) {
      dbManager.rollback();
      return CommandResult.error("Failed to add product: " + e.getMessage());
    }
  }
}
