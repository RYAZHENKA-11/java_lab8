package ru.app.object;

import java.io.Serializable;

/** Product units of measure. Used in {@link Product}. */
public enum UnitOfMeasure implements Serializable {
  /** Kilograms */
  KILOGRAMS,
  /** Liters */
  LITERS,
  /** Milliliters */
  MILLILITERS,
  /** Milligrams */
  MILLIGRAMS;

  private static final long serialVersionUID = 1L;
}
