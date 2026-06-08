package ru.app.command;

import ru.app.collection.Collection;
import ru.app.network.Request;

/**
 * Command to display available commands and their descriptions.
 *
 * <p>Returns a formatted list of all supported commands with brief usage descriptions. This is a
 * read-only command that does not modify the collection or the database.
 */
public class HelpCommand extends AbstractCommand {

  @Override
  public String getName() {
    return "help";
  }

  @Override
  public CommandResult execute(Request request, Collection collection) {
    String help =
        """
        Available commands:
          help - Show this help
          info - Information about the collection
          show - Display all elements
          add - Add a new element
          update id - Update the element with the specified id
          remove_by_id id - Delete the element by id
          clear - Clear the collection
          remove_first - Remove the first element
          add_if_min - Add the element if it is less than the minimum
          sum_of_price - Sum of prices
          average_of_price - Average price
          filter_by_unit_of_measure unit - Output elements with the specified unit""";
    return CommandResult.success(help);
  }
}
