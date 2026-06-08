package ru.app.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.app.object.Coordinates;
import ru.app.object.Location;
import ru.app.object.Person;
import ru.app.object.Product;
import ru.app.object.UnitOfMeasure;
import ru.app.util.PasswordHasher;

/**
 * Manages the connection to the PostgreSQL database and provides CRUD operations for {@link
 * Product} objects and user authentication.
 *
 * <p>Responsible for:
 *
 * <ul>
 *   <li>Establishing and closing database connections
 *   <li>Initializing the database schema (tables and sequences) at startup
 *   <li>Loading all products from the database into memory
 *   <li>Inserting, updating, and deleting individual products
 *   <li>Managing transactions for atomic operations
 *   <li>User registration and authentication
 * </ul>
 */
public class DatabaseManager {
  private static final Logger logger = LogManager.getLogger(DatabaseManager.class);

  private final String url;
  private final String user;
  private final String password;
  private Connection connection;

  /**
   * Creates a new DatabaseManager with the given connection parameters.
   *
   * @param config database connection configuration (must not be null)
   */
  public DatabaseManager(DbConfig config) {
    this.url = Objects.requireNonNull(config.url());
    this.user = Objects.requireNonNull(config.user());
    this.password = Objects.requireNonNull(config.password());
  }

  /**
   * Establishes a connection to the database.
   *
   * @throws SQLException if the connection cannot be established
   */
  public synchronized void connect() throws SQLException {
    connection = DriverManager.getConnection(url, user, password);
    connection.setAutoCommit(true);
    logger.info("Connected to database: {}", url);
  }

  /** Closes the database connection. Safe to call multiple times. */
  public synchronized void disconnect() {
    if (connection != null) {
      try {
        connection.close();
        logger.info("Disconnected from database");
      } catch (SQLException e) {
        logger.error("Error closing connection: {}", e.getMessage());
      } finally {
        connection = null;
      }
    }
  }

  /**
   * Initializes the database schema by executing the DDL script from {@code schema.sql}. Creates
   * the {@code products} table, {@code users} table, and {@code product_id_seq} sequence if they do
   * not already exist.
   *
   * @throws SQLException if schema initialization fails
   */
  public synchronized void initSchema() throws SQLException {
    String sql = loadSchemaSql();
    try (var stmt = connection.createStatement()) {
      stmt.execute(sql);
      logger.info("Database schema initialized");
    }
  }

  /**
   * Loads the SQL schema script from the classpath resource {@code schema.sql}.
   *
   * @return the full content of the schema SQL file
   * @throws RuntimeException if the resource is not found or cannot be read
   */
  private String loadSchemaSql() {
    try (var is = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
      if (is == null) throw new RuntimeException("schema.sql not found in resources");
      return new BufferedReader(new InputStreamReader(is))
          .lines()
          .reduce("", (a, b) -> a + "\n" + b);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load schema.sql", e);
    }
  }

  /**
   * Begins a database transaction by disabling auto-commit mode.
   *
   * <p>This method is synchronized to prevent concurrent transactions on the shared connection.
   *
   * @throws SQLException if the transaction cannot be started
   */
  public synchronized void beginTransaction() throws SQLException {
    connection.setAutoCommit(false);
  }

  /**
   * Commits the current transaction and restores auto-commit mode.
   *
   * <p>This method is synchronized to prevent concurrent transactions on the shared connection.
   *
   * @throws SQLException if the commit fails
   */
  public synchronized void commit() throws SQLException {
    try {
      connection.commit();
    } finally {
      connection.setAutoCommit(true);
    }
  }

  /** Rolls back the current transaction and restores auto-commit mode. */
  public synchronized void rollback() {
    try {
      connection.rollback();
    } catch (SQLException e) {
      logger.error("Failed to rollback: {}", e.getMessage());
    } finally {
      try {
        connection.setAutoCommit(true);
      } catch (SQLException ex) {
        logger.error("Failed to restore autocommit: {}", ex.getMessage());
      }
    }
  }

  /**
   * Registers a new user in the database.
   *
   * <p>This method is synchronized to prevent concurrent access to the shared database connection.
   *
   * @param login user login (must not be {@code null} or empty)
   * @param password plaintext password (must not be {@code null} or empty)
   * @throws SQLException if the user already exists or the insert fails
   */
  public synchronized void registerUser(String login, String password) throws SQLException {
    if (login == null || login.isEmpty())
      throw new IllegalArgumentException("'login' can't be null or empty.");
    if (password == null || password.isEmpty())
      throw new IllegalArgumentException("'password' can't be null or empty.");
    String hash = PasswordHasher.hash(password);
    String sql = "INSERT INTO users (login, password_hash) VALUES (?, ?)";
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, login);
      ps.setString(2, hash);
      ps.executeUpdate();
      logger.info("User registered: {}", login);
    }
  }

  /**
   * Authenticates a user by verifying the provided password against the stored hash.
   *
   * <p>This method is synchronized to prevent concurrent access to the shared database connection.
   *
   * @param login user login (must not be {@code null})
   * @param password plaintext password (must not be {@code null})
   * @return {@code true} if the credentials are valid, {@code false} otherwise
   * @throws SQLException if the query fails
   */
  public synchronized boolean authenticateUser(String login, String password) throws SQLException {
    if (login == null || password == null) return false;
    String sql = "SELECT password_hash FROM users WHERE login = ?";
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, login);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          String storedHash = rs.getString("password_hash");
          String inputHash = PasswordHasher.hash(password);
          return storedHash.equals(inputHash);
        }
        return false;
      }
    }
  }

  /**
   * Loads all products from the database, ordered by {@code id}.
   *
   * <p>This method is synchronized to prevent concurrent access to the shared database connection.
   *
   * @return list of all products stored in the database
   * @throws SQLException if the query fails
   */
  public synchronized List<Product> loadAll() throws SQLException {
    String sql =
        "SELECT p.id, p.name, p.price, p.part_number, p.unit_of_measure, "
            + "p.creation_date, p.created_by, "
            + "c.id AS coord_id, c.x AS coord_x, c.y AS coord_y, "
            + "o.id AS owner_id, o.name AS owner_name, o.birthday AS owner_birthday, "
            + "o.passport_id AS owner_passport_id, "
            + "l.id AS loc_id, l.x AS loc_x, l.y AS loc_y, l.z AS loc_z, l.name AS loc_name "
            + "FROM products p "
            + "JOIN coordinates c ON p.coordinate_id = c.id "
            + "JOIN owners o ON p.owner_id = o.id "
            + "JOIN locations l ON o.location_id = l.id "
            + "ORDER BY p.id";
    List<Product> products = new ArrayList<>();
    try (PreparedStatement ps = connection.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) products.add(mapRowToProduct(rs));
    }
    logger.info("Loaded {} products from database", products.size());
    return products;
  }

  /**
   * Inserts a new product into the database. The product {@code id} is generated by the PostgreSQL
   * sequence {@code product_id_seq}.
   *
   * <p>This method is synchronized to prevent concurrent access to the shared database connection.
   *
   * @param product the product to insert (must not be null)
   * @param createdBy login of the user creating the product (must not be {@code null})
   * @return the generated {@code id} of the inserted product
   * @throws SQLException if the insert fails (e.g., constraint violation)
   */
  public synchronized Integer insertProduct(Product product, String createdBy) throws SQLException {
    String insertCoord = "INSERT INTO coordinates (x, y) VALUES (?, ?) RETURNING id";
    String insertLoc = "INSERT INTO locations (x, y, z, name) VALUES (?, ?, ?, ?) RETURNING id";
    String insertOwner =
        "INSERT INTO owners (name, birthday, passport_id, location_id) VALUES (?, ?, ?, ?) RETURNING id";
    String insertProductSql =
        "INSERT INTO products (name, coordinate_id, creation_date, price, part_number, "
            + "unit_of_measure, owner_id, created_by) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

    long coordId;
    long locId;
    int ownerId;
    Integer productId;

    try (PreparedStatement psCoord = connection.prepareStatement(insertCoord)) {
      psCoord.setInt(1, product.coordinates().x());
      psCoord.setFloat(2, product.coordinates().y());
      try (ResultSet rs = psCoord.executeQuery()) {
        if (!rs.next()) throw new SQLException("INSERT coordinates did not return id");
        coordId = rs.getLong("id");
      }
    }

    try (PreparedStatement psLoc = connection.prepareStatement(insertLoc)) {
      psLoc.setFloat(1, product.owner().location().x());
      psLoc.setInt(2, product.owner().location().y());
      psLoc.setLong(3, product.owner().location().z());
      psLoc.setString(4, product.owner().location().name());
      try (ResultSet rs = psLoc.executeQuery()) {
        if (!rs.next()) throw new SQLException("INSERT location did not return id");
        locId = rs.getLong("id");
      }
    }

    try (PreparedStatement psOwner = connection.prepareStatement(insertOwner)) {
      psOwner.setString(1, product.owner().name());
      setTimestampOrNull(psOwner, 2, product.owner().birthday());
      psOwner.setString(3, product.owner().passportID());
      psOwner.setLong(4, locId);
      try (ResultSet rs = psOwner.executeQuery()) {
        if (!rs.next()) throw new SQLException("INSERT owner did not return id");
        ownerId = rs.getInt("id");
      }
    }

    try (PreparedStatement psProduct = connection.prepareStatement(insertProductSql)) {
      psProduct.setString(1, product.name());
      psProduct.setLong(2, coordId);
      psProduct.setTimestamp(3, Timestamp.from(product.creationDate().toInstant()));
      psProduct.setObject(4, product.price());
      psProduct.setString(5, product.partNumber());
      psProduct.setString(6, product.unitOfMeasure().name());
      psProduct.setInt(7, ownerId);
      psProduct.setString(8, createdBy);
      try (ResultSet rs = psProduct.executeQuery()) {
        if (!rs.next()) throw new SQLException("INSERT product did not return id");
        productId = rs.getInt("id");
      }
    }

    logger.info("Inserted product with id: {}", productId);
    return productId;
  }

  /**
   * Updates an existing product in the database by its {@code id}.
   *
   * <p>The {@code created_by} field is intentionally not updated — the original creator is
   * preserved.
   *
   * <p>This method is synchronized to prevent concurrent access to the shared database connection.
   *
   * @param id the identifier of the product to update
   * @param product the new product data (the {@code id} field of the product is ignored; the {@code
   *     id} parameter is used instead)
   * @return {@code true} if a product with the given {@code id} was found and updated, {@code
   *     false} otherwise
   * @throws SQLException if the update fails
   */
  public synchronized boolean updateProduct(Integer id, Product product) throws SQLException {
    String getProductSql = "SELECT coordinate_id, owner_id FROM products WHERE id = ?";
    long coordId;
    int ownerId;

    try (PreparedStatement ps = connection.prepareStatement(getProductSql)) {
      ps.setInt(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return false;
        coordId = rs.getLong("coordinate_id");
        ownerId = rs.getInt("owner_id");
      }
    }

    String getOwnerSql = "SELECT location_id FROM owners WHERE id = ?";
    long locationId;
    try (PreparedStatement ps = connection.prepareStatement(getOwnerSql)) {
      ps.setInt(1, ownerId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return false;
        locationId = rs.getLong("location_id");
      }
    }

    String updateCoord = "UPDATE coordinates SET x=?, y=? WHERE id=?";
    try (PreparedStatement ps = connection.prepareStatement(updateCoord)) {
      ps.setInt(1, product.coordinates().x());
      ps.setFloat(2, product.coordinates().y());
      ps.setLong(3, coordId);
      ps.executeUpdate();
    }

    String updateLoc = "UPDATE locations SET x=?, y=?, z=?, name=? WHERE id=?";
    try (PreparedStatement ps = connection.prepareStatement(updateLoc)) {
      ps.setFloat(1, product.owner().location().x());
      ps.setInt(2, product.owner().location().y());
      ps.setLong(3, product.owner().location().z());
      ps.setString(4, product.owner().location().name());
      ps.setLong(5, locationId);
      ps.executeUpdate();
    }

    String updateOwner = "UPDATE owners SET name=?, birthday=?, passport_id=? WHERE id=?";
    try (PreparedStatement ps = connection.prepareStatement(updateOwner)) {
      ps.setString(1, product.owner().name());
      setTimestampOrNull(ps, 2, product.owner().birthday());
      ps.setString(3, product.owner().passportID());
      ps.setInt(4, ownerId);
      ps.executeUpdate();
    }

    String updateProductSql =
        "UPDATE products SET name=?, coordinate_id=?, price=?, part_number=?, "
            + "unit_of_measure=?, owner_id=? WHERE id=?";
    try (PreparedStatement ps = connection.prepareStatement(updateProductSql)) {
      ps.setString(1, product.name());
      ps.setLong(2, coordId);
      ps.setObject(3, product.price());
      ps.setString(4, product.partNumber());
      ps.setString(5, product.unitOfMeasure().name());
      ps.setInt(6, ownerId);
      ps.setInt(7, id);
      return ps.executeUpdate() == 1;
    }
  }

  /**
   * Deletes a product from the database by its {@code id}.
   *
   * <p>This method is synchronized to prevent concurrent access to the shared database connection.
   *
   * @param id the identifier of the product to delete
   * @return {@code true} if a product with the given {@code id} was found and deleted, {@code
   *     false} otherwise
   * @throws SQLException if the delete fails
   */
  public synchronized boolean deleteProduct(Integer id) throws SQLException {
    String getIdsSql = "SELECT coordinate_id, owner_id FROM products WHERE id = ?";
    long coordId;
    int ownerId;

    try (PreparedStatement ps = connection.prepareStatement(getIdsSql)) {
      ps.setInt(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return false;
        coordId = rs.getLong("coordinate_id");
        ownerId = rs.getInt("owner_id");
      }
    }

    String deleteProductSql = "DELETE FROM products WHERE id = ?";
    try (PreparedStatement ps = connection.prepareStatement(deleteProductSql)) {
      ps.setInt(1, id);
      if (ps.executeUpdate() != 1) return false;
    }

    String getLocIdSql = "SELECT location_id FROM owners WHERE id = ?";
    Long locId;
    try (PreparedStatement ps = connection.prepareStatement(getLocIdSql)) {
      ps.setInt(1, ownerId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) locId = rs.getLong("location_id");
        else locId = null;
      }
    }

    String deleteOwnerSql = "DELETE FROM owners WHERE id = ?";
    try (PreparedStatement ps = connection.prepareStatement(deleteOwnerSql)) {
      ps.setInt(1, ownerId);
      ps.executeUpdate();
    }

    if (locId != null) {
      String deleteLocSql = "DELETE FROM locations WHERE id = ?";
      try (PreparedStatement ps = connection.prepareStatement(deleteLocSql)) {
        ps.setLong(1, locId);
        ps.executeUpdate();
      }
    }

    String deleteCoordSql = "DELETE FROM coordinates WHERE id = ?";
    try (PreparedStatement ps = connection.prepareStatement(deleteCoordSql)) {
      ps.setLong(1, coordId);
      ps.executeUpdate();
    }

    logger.info("Deleted product with id: {}", id);
    return true;
  }

  /**
   * Checks if a product with the given ID exists and returns the login of the user who created it.
   *
   * @param id the product ID to check
   * @return the created_by login, or {@code null} if the product doesn't exist
   * @throws SQLException if the query fails
   */
  public synchronized String getProductOwner(Integer id) throws SQLException {
    String sql = "SELECT created_by FROM products WHERE id = ?";
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setInt(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getString("created_by");
        }
        return null;
      }
    }
  }

  /**
   * Deletes all products belonging to a specific user from the database, including their related
   * coordinates, owners, and locations.
   *
   * <p>This method is synchronized to prevent concurrent access to the shared database connection.
   *
   * @param createdBy login of the user whose products are to be deleted
   * @throws SQLException if the delete fails
   */
  public synchronized void clearUserProducts(String createdBy) throws SQLException {
    String getProductsSql = "SELECT id, coordinate_id, owner_id FROM products WHERE created_by = ?";
    List<DeleteIds> productIds = new ArrayList<>();

    try (PreparedStatement ps = connection.prepareStatement(getProductsSql)) {
      ps.setString(1, createdBy);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          productIds.add(
              new DeleteIds(rs.getInt("id"), rs.getLong("coordinate_id"), rs.getInt("owner_id")));
        }
      }
    }

    for (DeleteIds ids : productIds) {
      deleteRelatedRecords(ids.coordId(), ids.ownerId());
    }

    String deleteProductsSql = "DELETE FROM products WHERE created_by = ?";
    try (PreparedStatement ps = connection.prepareStatement(deleteProductsSql)) {
      ps.setString(1, createdBy);
      ps.executeUpdate();
    }
  }

  private void deleteRelatedRecords(Long coordId, Integer ownerId) throws SQLException {
    Long locId = null;
    if (ownerId != null) {
      String getLocIdSql = "SELECT location_id FROM owners WHERE id = ?";
      try (PreparedStatement ps = connection.prepareStatement(getLocIdSql)) {
        ps.setInt(1, ownerId);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) locId = rs.getLong("location_id");
        }
      }
      String deleteOwnerSql = "DELETE FROM owners WHERE id = ?";
      try (PreparedStatement ps = connection.prepareStatement(deleteOwnerSql)) {
        ps.setInt(1, ownerId);
        ps.executeUpdate();
      }
    }
    if (locId != null) {
      String deleteLocSql = "DELETE FROM locations WHERE id = ?";
      try (PreparedStatement ps = connection.prepareStatement(deleteLocSql)) {
        ps.setLong(1, locId);
        ps.executeUpdate();
      }
    }
    if (coordId != null) {
      String deleteCoordSql = "DELETE FROM coordinates WHERE id = ?";
      try (PreparedStatement ps = connection.prepareStatement(deleteCoordSql)) {
        ps.setLong(1, coordId);
        ps.executeUpdate();
      }
    }
  }

  /**
   * Maps a single row from the {@code products} table to a {@link Product} object.
   *
   * @param rs the result set positioned at the current row
   * @return a fully constructed Product object
   * @throws SQLException if any column cannot be read
   */
  private Product mapRowToProduct(ResultSet rs) throws SQLException {
    Integer id = rs.getInt("id");
    String name = rs.getString("name");
    Coordinates coordinates = new Coordinates(rs.getInt("coord_x"), rs.getFloat("coord_y"));
    Float price = rs.getObject("price", Float.class);
    String partNumber = rs.getString("part_number");
    UnitOfMeasure unitOfMeasure = UnitOfMeasure.valueOf(rs.getString("unit_of_measure"));

    String ownerName = rs.getString("owner_name");
    Date ownerBirthday = null;
    Timestamp bdayTs = rs.getTimestamp("owner_birthday");
    if (bdayTs != null) ownerBirthday = new Date(bdayTs.getTime());
    String ownerPassportId = rs.getString("owner_passport_id");
    Location location =
        new Location(
            rs.getFloat("loc_x"),
            rs.getInt("loc_y"),
            rs.getLong("loc_z"),
            rs.getString("loc_name"));
    Person owner = new Person(ownerName, ownerBirthday, ownerPassportId, location);
    Timestamp creationTs = rs.getTimestamp("creation_date");
    ZonedDateTime creationDate = creationTs.toInstant().atZone(ZoneId.systemDefault());
    String createdBy = rs.getString("created_by");

    return new Product(
        id, name, coordinates, price, partNumber, unitOfMeasure, owner, creationDate, createdBy);
  }

  /**
   * Sets a {@code TIMESTAMP} parameter to the given date value, or to {@code NULL} if the value is
   * null.
   *
   * @param ps the prepared statement
   * @param index the 1-based parameter index
   * @param date the date value, or {@code null}
   * @throws SQLException if the parameter cannot be set
   */
  private void setTimestampOrNull(PreparedStatement ps, int index, Date date) throws SQLException {
    if (date != null) ps.setTimestamp(index, new Timestamp(date.getTime()));
    else ps.setNull(index, java.sql.Types.TIMESTAMP);
  }

  private record DeleteIds(Integer productId, Long coordId, Integer ownerId) {}
}
