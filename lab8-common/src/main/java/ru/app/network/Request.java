package ru.app.network;

import java.io.Serializable;
import ru.app.object.Product;

/**
 * Request object sent from client to server over UDP.
 *
 * <p>Contains the command type, associated data for execution, and user credentials for
 * authentication. This record is serialized using Java serialization before being transmitted over
 * the network.
 *
 * @param type the command to execute (must not be {@code null})
 * @param data optional payload, typically a {@link Product} for {@code add} and {@code update}
 * @param id optional identifier, used by {@code remove_by_id} and {@code update} commands
 * @param argument optional string argument, used by {@code filter_by_unit_of_measure} and {@code
 *     execute_script} commands
 * @param login user login for authentication (may be {@code null} for REGISTER command)
 * @param password plaintext password for authentication (may be {@code null} for REGISTER command)
 */
public record Request(
    CommandType type, Object data, Integer id, String argument, String login, String password)
    implements Serializable {

  /**
   * Creates a request with command type and product data (no authentication).
   *
   * @param type command type (must not be {@code null})
   * @param data product data to be sent with the request
   */
  public Request(CommandType type, Object data) {
    this(type, data, null, null, null, null);
  }

  /**
   * Creates a request with only command type (no additional data).
   *
   * @param type command type (must not be {@code null})
   */
  public Request(CommandType type) {
    this(type, null, null, null, null, null);
  }

  /**
   * Extracts the product from the {@code data} field.
   *
   * @return the product if {@code data} is an instance of {@link Product}, or {@code null}
   *     otherwise
   */
  public Product product() {
    return data instanceof Product p ? p : null;
  }
}
