package ru.app.object;

import java.io.Serial;
import java.io.Serializable;

/**
 * Physical location of a person.
 *
 * <p>Contains 3D coordinates ({@code x}, {@code y}, {@code z}) and a place name. The {@code y}
 * coordinate and {@code name} are required; the name length must not exceed 986. Used as a nested
 * component of {@link Person}.
 *
 * @param x X coordinate
 * @param y Y coordinate (must not be {@code null})
 * @param z Z coordinate
 * @param name place name (must not be {@code null}, length ≤ 986)
 */
public record Location(float x, Integer y, long z, String name) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a location with validation of constraints.
   *
   * @param x X coordinate
   * @param y Y coordinate (must not be {@code null})
   * @param z Z coordinate
   * @param name place name (must not be {@code null}, length ≤ 986)
   * @throws IllegalArgumentException if {@code y} is {@code null}, {@code name} is {@code null}, or
   *     name length exceeds 986
   */
  public Location(float x, Integer y, long z, String name) {
    if (y == null) throw new IllegalArgumentException("'y' can't be null.");
    this.y = y;

    if (name == null) throw new IllegalArgumentException("'name' can't be null.");
    if (name.length() > 986) throw new IllegalArgumentException("'name' length can't be > 986.");
    this.name = name;

    this.x = x;
    this.z = z;
  }
}
