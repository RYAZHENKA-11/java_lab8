package ru.app.command;

import ru.app.collection.Collection;
import ru.app.network.Request;

/**
 * Command to calculate and display the sum of prices of all elements.
 *
 * <p>Returns the pre-maintained sum of all product prices. Products with {@code null} price are not
 * included in the sum. This is a read-only command that does not modify the collection or the
 * database.
 */
public class SumOfPriceCommand extends AbstractCommand {

  @Override
  public String getName() {
    return "sum_of_price";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    return CommandResult.success("Sum of prices: " + collection.sumPrice());
  }
}
