package ru.app.object;

import java.io.Serial;
import java.io.Serializable;

/**
 * Product coordinates in 2D space.
 *
 * <p>Both {@code x} and {@code y} fields are required and must not be {@code null}. Used as a
 * nested component of {@link Product}.
 *
 * @param x X coordinate (must not be {@code null})
 * @param y Y coordinate (must not be {@code null})
 */
public record Coordinates(Integer x, Float y) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates coordinates with null validation.
   *
   * @param x X coordinate (must not be {@code null})
   * @param y Y coordinate (must not be {@code null})
   * @throws IllegalArgumentException if either {@code x} or {@code y} is {@code null}
   */
  public Coordinates(Integer x, Float y) {
    if (x == null) throw new IllegalArgumentException("'x' can't be null.");
    this.x = x;

    if (y == null) throw new IllegalArgumentException("'y' can't be null.");
    this.y = y;
  }
}
