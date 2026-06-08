package ru.app.command;

import ru.app.collection.Collection;
import ru.app.network.Request;

/**
 * Command to calculate and display the average price of all elements.
 *
 * <p>Computed as {@code sumPrice / collectionSize}. Returns {@code 0.0} if the collection is empty.
 * This is a read-only command that does not modify the collection or the database.
 */
public class AverageOfPriceCommand extends AbstractCommand {

  @Override
  public String getName() {
    return "average_of_price";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    return CommandResult.success(String.valueOf(collection.averagePrice()));
  }
}
