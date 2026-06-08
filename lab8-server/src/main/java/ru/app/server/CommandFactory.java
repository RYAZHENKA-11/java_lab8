package ru.app.server;

import ru.app.command.AbstractCommand;
import ru.app.command.AddCommand;
import ru.app.command.AddIfMinCommand;
import ru.app.command.AverageOfPriceCommand;
import ru.app.command.ClearCommand;
import ru.app.command.ExecuteScriptCommand;
import ru.app.command.ExitCommand;
import ru.app.command.FilterByUnitOfMeasureCommand;
import ru.app.command.HelpCommand;
import ru.app.command.HistoryCommand;
import ru.app.command.InfoCommand;
import ru.app.command.LoginCommand;
import ru.app.command.RegisterCommand;
import ru.app.command.RemoveByIdCommand;
import ru.app.command.RemoveFirstCommand;
import ru.app.command.SaveCommand;
import ru.app.command.ShowCommand;
import ru.app.command.SumOfPriceCommand;
import ru.app.command.UpdateCommand;
import ru.app.db.DatabaseManager;
import ru.app.network.CommandType;

/**
 * Factory for creating command objects based on command type.
 *
 * <p>Maps {@link CommandType} enum values to corresponding command implementations. Modification
 * commands (add, update, remove, clear) require a {@link DatabaseManager} instance to persist
 * changes; read-only commands do not.
 */
public class CommandFactory {

  /**
   * Creates a command based on command type.
   *
   * @param type command type
   * @param dbManager the database manager, passed to modification commands
   * @return command instance
   */
  public AbstractCommand create(CommandType type, DatabaseManager dbManager) {
    return switch (type) {
      case HELP -> new HelpCommand();
      case INFO -> new InfoCommand();
      case SHOW -> new ShowCommand();
      case ADD -> new AddCommand(dbManager);
      case UPDATE -> new UpdateCommand(dbManager);
      case REMOVE_BY_ID -> new RemoveByIdCommand(dbManager);
      case CLEAR -> new ClearCommand(dbManager);
      case SAVE -> new SaveCommand();
      case EXIT -> new ExitCommand();
      case REMOVE_FIRST -> new RemoveFirstCommand(dbManager);
      case ADD_IF_MIN -> new AddIfMinCommand(dbManager);
      case HISTORY -> new HistoryCommand();
      case SUM_OF_PRICE -> new SumOfPriceCommand();
      case AVERAGE_OF_PRICE -> new AverageOfPriceCommand();
      case FILTER_BY_UNIT_OF_MEASURE -> new FilterByUnitOfMeasureCommand();
      case EXECUTE_SCRIPT -> new ExecuteScriptCommand();
      case REGISTER -> new RegisterCommand(dbManager);
      case LOGIN -> new LoginCommand();
    };
  }
}
