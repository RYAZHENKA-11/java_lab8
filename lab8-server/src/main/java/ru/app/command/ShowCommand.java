package ru.app.command;

import java.util.Comparator;
import java.util.List;
import ru.app.collection.Collection;
import ru.app.network.Request;
import ru.app.object.Product;

/**
 * Command to display all products in the collection sorted by name.
 *
 * <p>Retrieves a copy of the in-memory collection, sorts it alphabetically by product name, and
 * returns the sorted list. This is a read-only command that does not modify the collection or the
 * database. Returns "Empty." if the collection contains no products.
 */
public class ShowCommand extends AbstractCommand {

  @Override
  public String getName() {
    return "show";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    if (collection.isEmpty()) return CommandResult.success("Empty.");
    List<Product> products =
        collection.products().stream().sorted(Comparator.comparing(Product::name)).toList();
    return CommandResult.success("Products:", products);
  }
}
