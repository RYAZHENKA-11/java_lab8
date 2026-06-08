package ru.app.util;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Manages application-wide locale switching and resource bundle loading.
 *
 * <p>Singleton that holds the current {@link Locale}, the corresponding {@link ResourceBundle}
 * loaded from {@code locales/messages}, and provides convenience methods for localized string
 * formatting, number formatting, and date/time formatting.
 *
 * <p>When the locale is changed via {@link #setLocale(Locale)}, the new locale is also set as the
 * JVM default ({@link Locale#setDefault(Locale)}) so that JavaFX components and other libraries
 * that rely on the default locale pick up the change automatically.
 */
public class LocaleManager {

  public static final Locale[] AVAILABLE_LOCALES = {
    Locale.forLanguageTag("en-CA"),
    Locale.forLanguageTag("ru-RU"),
    Locale.forLanguageTag("nl-NL"),
    Locale.forLanguageTag("da-DK")
  };
  private static final LocaleManager INSTANCE = new LocaleManager();
  private final ObjectProperty<Locale> localeProperty =
      new SimpleObjectProperty<>(Locale.getDefault());

  private ResourceBundle bundle;

  private LocaleManager() {
    loadBundle(Locale.getDefault());
  }

  public static LocaleManager getInstance() {
    return INSTANCE;
  }

  public ObjectProperty<Locale> localeProperty() {
    return localeProperty;
  }

  /**
   * Changes the current locale, reloads the resource bundle and updates {@link Locale#getDefault()}
   * to the new value.
   *
   * <p>If the given locale is the same as the current one, this is a no-op.
   *
   * @param locale the new locale to apply
   */
  public void setLocale(Locale locale) {
    if (!locale.equals(localeProperty.get())) {
      loadBundle(locale);
      Locale.setDefault(locale);
      localeProperty.set(locale);
    }
  }

  /**
   * Returns the currently active locale.
   *
   * @return the current locale
   */
  public Locale getCurrentLocale() {
    return localeProperty.get();
  }

  /**
   * Returns the currently loaded resource bundle.
   *
   * @return the resource bundle for the active locale
   */
  public ResourceBundle getBundle() {
    return bundle;
  }

  /**
   * Retrieves a localized string for the given key from the resource bundle.
   *
   * <p>If arguments are provided, the string is formatted using {@link MessageFormat#format(String,
   * Object...)}.
   *
   * @param key the bundle key
   * @param args optional arguments for message formatting
   * @return the localized string, or {@code "!key!"} if the key is not found
   */
  public String getString(String key, Object... args) {
    try {
      String pattern = bundle.getString(key);
      if (args.length > 0) return MessageFormat.format(pattern, args);
      return pattern;
    } catch (Exception e) {
      return "!" + key + "!";
    }
  }

  /**
   * Returns the localized display name of a locale from the resource bundle.
   *
   * <p>The key is constructed as {@code lang.<language>_<country>}. For example, {@code
   * lang.en_CA}.
   *
   * @param locale the locale whose display name to look up
   * @return the localized name, or {@code "!key!"} if not found
   */
  public String getLanguageDisplayName(Locale locale) {
    String key = "lang." + locale.getLanguage() + "_" + locale.getCountry();
    return getString(key);
  }

  /**
   * Returns a {@link NumberFormat} instance configured for the current locale.
   *
   * @return a number formatter for the active locale
   */
  public NumberFormat getNumberFormat() {
    return NumberFormat.getInstance(localeProperty.get());
  }

  /**
   * Returns a {@link DateTimeFormatter} for date-time display (MEDIUM style) configured for the
   * current locale.
   *
   * @return a date-time formatter for the active locale
   */
  public DateTimeFormatter getDateTimeFormatter() {
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(localeProperty.get());
  }

  /**
   * Returns a {@link DateTimeFormatter} for date-only display (SHORT style) configured for the
   * current locale.
   *
   * @return a date formatter for the active locale
   */
  public DateTimeFormatter getDateFormatter() {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(localeProperty.get());
  }

  /**
   * Loads the resource bundle for the given locale from the classpath.
   *
   * <p>The bundle base name is {@code locales.messages}, which resolves to {@code
   * /locales/messages[_<locale>].properties}.
   *
   * @param locale the locale to load the bundle for
   */
  private void loadBundle(Locale locale) {
    bundle = ResourceBundle.getBundle("locales.messages", locale);
  }
}
