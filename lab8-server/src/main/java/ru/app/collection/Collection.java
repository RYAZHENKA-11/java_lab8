package ru.app.collection;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import ru.app.object.Product;
import ru.app.object.UnitOfMeasure;

/**
 * Manages a collection of {@link Product} objects stored in a priority queue (natural order by
 * {@code id}). Ensures uniqueness of identifiers and part numbers, and maintains the sum of all
 * product prices.
 *
 * <p>The collection is created with a fixed creation date. All add, update, and remove operations
 * automatically adjust the internal unique key sets and price sum.
 *
 * <p>Collection is loaded from the database at startup; all modifications are persisted to the
 * database first, then applied to the in-memory collection.
 */
public class Collection {
  private final PriorityQueue<Product> collection = new PriorityQueue<>();
  private final Set<Integer> ids = new HashSet<>();
  private final Set<String> partNumbers = new HashSet<>();
  private final ZonedDateTime creationDate;
  private Double sumPrice = 0.0;

  /** Creates an empty collection, setting the current date and time as the creation date. */
  public Collection() {
    creationDate = ZonedDateTime.now();
  }

  /**
   * Creates an empty collection with a given creation date.
   *
   * @param creationDate collection creation date (cannot be {@code null})
   * @throws IllegalArgumentException if {@code creationDate == null}
   */
  public Collection(ZonedDateTime creationDate) throws IllegalArgumentException {
    if (creationDate == null) throw new IllegalArgumentException("'creationDate' can't be null");
    this.creationDate = creationDate;
  }

  /**
   * Returns the current number of products in the collection.
   *
   * @return collection size
   */
  public synchronized int size() {
    return collection.size();
  }

  /**
   * Returns the collection creation date.
   *
   * @return creation date (not {@code null})
   */
  public ZonedDateTime creationDate() {
    return creationDate;
  }

  /**
   * Returns a copy of the internal product queue.
   *
   * @return new {@link PriorityQueue} with copies of all products
   */
  public synchronized PriorityQueue<Product> products() {
    return new PriorityQueue<>(collection);
  }

  /**
   * Adds a new product to the collection.
   *
   * <p>Uniqueness of {@code id} and {@code partNumber} is checked before adding. If the product is
   * successfully added, its price (if not {@code null}) is added to the total sum.
   *
   * @param product product to add (validated through {@link Product} constructor)
   * @throws IllegalArgumentException if {@code id} or {@code partNumber} are not unique
   */
  public synchronized void add(Product product) throws IllegalArgumentException {
    if (!ids.add(product.id())) throw new IllegalArgumentException("'id' must be unique.");
    if (!partNumbers.add(product.partNumber())) {
      ids.remove(product.id());
      throw new IllegalArgumentException("'partNumber' must be unique.");
    }
    collection.add(product);
    if (product.price() != null) sumPrice += product.price();
  }

  /**
   * Updates the product with the specified identifier, replacing it with new data.
   *
   * <p>Actually performs removal of the old product and addition of a new one with the same {@code
   * id}. All uniqueness constraints apply to the new product (except the {@code id} itself, which
   * remains unchanged).
   *
   * @param id identifier of the product to update (must exist in the collection)
   * @param product new product whose fields will be assigned (except {@code id})
   * @throws IllegalArgumentException if product with specified {@code id} does not exist, or if new
   *     {@code partNumber} is not unique
   */
  public synchronized void update(Integer id, Product product) throws IllegalArgumentException {
    Product oldProduct = remove(id);
    Product newProduct =
        new Product(
            id,
            product.name(),
            product.coordinates(),
            product.price(),
            product.partNumber(),
            product.unitOfMeasure(),
            product.owner(),
            oldProduct.creationDate(),
            oldProduct.createdBy());
    try {
      add(newProduct);
    } catch (IllegalArgumentException e) {
      add(oldProduct);
      throw e;
    }
  }

  /**
   * Removes all products created by the specified user from the collection.
   *
   * <p>Adjusts the unique key sets and price sum for each removed product.
   *
   * @param login login of the user whose products are to be removed
   */
  public synchronized void removeByUser(String login) {
    if (login == null) return;
    collection.removeIf(
        product -> {
          if (login.equals(product.createdBy())) {
            ids.remove(product.id());
            partNumbers.remove(product.partNumber());
            if (product.price() != null) sumPrice -= product.price();
            return true;
          }
          return false;
        });
  }

  /**
   * Removes the product with the specified identifier from the collection.
   *
   * <p>After removal, the price sum is adjusted and corresponding unique keys are freed.
   *
   * @param id identifier of the product to remove
   * @throws IllegalArgumentException if product with specified {@code id} is not found
   */
  public synchronized Product remove(Integer id) throws IllegalArgumentException {
    Product product = collection.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
    if (product == null)
      throw new IllegalArgumentException("Product with id " + id + " doesn't exist.");
    collection.remove(product);
    ids.remove(product.id());
    partNumbers.remove(product.partNumber());
    if (product.price() != null) sumPrice -= product.price();
    return product;
  }

  /**
   * Checks whether the collection contains no products.
   *
   * @return {@code true} if the collection is empty
   */
  public synchronized boolean isEmpty() {
    return collection.isEmpty();
  }

  /**
   * Retrieves, but does not remove, the head of the priority queue (product with the smallest
   * {@code id}).
   *
   * @return the head of the queue, or {@code null} if the queue is empty
   */
  public synchronized Product peek() {
    return collection.peek();
  }

  /**
   * Returns the minimum ID in the collection, or {@code null} if empty.
   *
   * @return min id or {@code null}
   */
  public synchronized Integer getMinId() {
    if (isEmpty()) {
      return null;
    }
    return peek().id();
  }

  /**
   * Atomically retrieves and removes the first (smallest by {@code id}) element from the queue.
   *
   * @return the removed product, or {@code null} if the collection is empty
   */
  public synchronized Product pollFirst() {
    if (isEmpty()) {
      return null;
    }
    return removeFirst();
  }

  /**
   * Removes the first (smallest by {@code id}) element from the queue and returns it.
   *
   * @throws java.util.NoSuchElementException if the collection is empty
   */
  public synchronized Product removeFirst() {
    Product product = collection.remove();
    ids.remove(product.id());
    partNumbers.remove(product.partNumber());
    if (product.price() != null) sumPrice -= product.price();
    return product;
  }

  /**
   * Returns the sum of prices of all products in the collection.
   *
   * <p>Products with {@code null} price are not included. The sum is maintained up-to-date for all
   * collection changes.
   *
   * @return sum of prices (always not {@code null}, initial value 0.0)
   */
  public synchronized Double sumPrice() {
    return sumPrice;
  }

  /**
   * Returns the average arithmetic price of products.
   *
   * <p>Calculated as {@link #sumPrice()} / {@link #size()}. If the collection is empty, returns
   * {@code 0.0}.
   *
   * @return average price
   */
  public synchronized Double averagePrice() {
    if (size() == 0) return 0.0;
    return sumPrice / size();
  }

  /**
   * Returns a list of products whose unit of measure matches the specified one.
   *
   * @param unitOfMeasure the unit of measure to filter by (not {@code null})
   * @return list of products (can be empty)
   */
  public synchronized List<Product> filterByUnitOfMeasure(UnitOfMeasure unitOfMeasure) {
    return collection.stream().filter(x -> x.unitOfMeasure().equals(unitOfMeasure)).toList();
  }
}
