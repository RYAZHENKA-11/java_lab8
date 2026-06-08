package ru.app.object;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * Person (product owner).
 *
 * <p>Contains the owner's name, optional birthday, optional passport ID, and required {@link
 * Location}. The name must not be {@code null} or empty.
 *
 * @param name owner's name (must not be {@code null} or empty)
 * @param birthday date of birth (may be {@code null})
 * @param passportID passport identifier (may be {@code null})
 * @param location physical location (must not be {@code null})
 */
public record Person(String name, Date birthday, String passportID, Location location)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a person with validation of domain constraints.
   *
   * @param name name (must not be {@code null} or empty)
   * @param birthday birthday (may be {@code null})
   * @param passportID passport identifier (may be {@code null})
   * @param location physical location (must not be {@code null})
   * @throws IllegalArgumentException if name is {@code null} or empty, or location is {@code null}
   */
  public Person(String name, Date birthday, String passportID, Location location) {
    if (name == null) throw new IllegalArgumentException("'name' can't be null.");
    if (name.isEmpty()) throw new IllegalArgumentException("'name' can't be empty.");
    this.name = name;

    this.birthday = birthday;

    this.passportID = passportID;

    if (location == null) throw new IllegalArgumentException("'location' can't be null.");
    this.location = location;
  }
}
