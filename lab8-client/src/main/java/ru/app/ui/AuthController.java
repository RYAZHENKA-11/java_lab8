package ru.app.ui;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ru.app.client.ClientConnectionModule;
import ru.app.network.CommandType;
import ru.app.network.Request;
import ru.app.util.LocaleManager;

/**
 * Controller for the authentication window (login / registration).
 *
 * <p>Manages the login and registration forms, language switching via a ComboBox, and communicates
 * with the server through {@link ClientConnectionModule}. All user-visible text is localized
 * through {@link LocaleManager} and updates on-the-fly when the locale is changed.
 */
public class AuthController {

  @FXML private ToggleGroup toggleGroup;
  @FXML private ToggleButton loginToggle;
  @FXML private ToggleButton registerToggle;

  @FXML private VBox loginForm;
  @FXML private VBox registerForm;

  @FXML private TextField loginField;
  @FXML private PasswordField passwordField;
  @FXML private Button loginButton;
  @FXML private ProgressIndicator loginProgress;
  @FXML private Label loginFieldLabel;
  @FXML private Label passwordFieldLabel;

  @FXML private TextField regLoginField;
  @FXML private PasswordField regPasswordField;
  @FXML private PasswordField regConfirmField;
  @FXML private Button registerButton;
  @FXML private ProgressIndicator registerProgress;
  @FXML private Label regLoginFieldLabel;
  @FXML private Label regPasswordFieldLabel;
  @FXML private Label regConfirmFieldLabel;

  @FXML private Label languageLabel;
  @FXML private MenuButton languageMenu;

  @FXML private Label headerLabel;
  @FXML private Label errorLabel;

  private ClientConnectionModule connection;
  private Stage stage;
  private BiConsumer<String, String> onAuthSuccess;

  /**
   * Initializes the controller after FXML loading.
   *
   * <p>Sets up the language ComboBox with available locales, attaches listeners for locale
   * switching and form submission, sets up toggle group for login/register switching, and applies
   * the current locale's labels.
   */
  @FXML
  public void initialize() {
    LocaleManager lm = LocaleManager.getInstance();

    for (Locale locale : LocaleManager.AVAILABLE_LOCALES) {
      String nativeName = locale.getDisplayName(locale);
      MenuItem item =
          new MenuItem(Character.toUpperCase(nativeName.charAt(0)) + nativeName.substring(1));
      item.setUserData(locale);
      item.setOnAction(
          e -> {
            Locale selected = (Locale) item.getUserData();
            if (!selected.equals(lm.getCurrentLocale())) {
              lm.setLocale(selected);
            }
          });
      languageMenu.getItems().add(item);
    }

    lm.localeProperty()
        .addListener(
            (obs, old, val) -> {
              updateLocale();
              String name = val.getDisplayName(val);
              languageMenu.setText(Character.toUpperCase(name.charAt(0)) + name.substring(1));
            });

    Locale cur = lm.getCurrentLocale();
    String curName = cur.getDisplayName(cur);
    languageMenu.setText(Character.toUpperCase(curName.charAt(0)) + curName.substring(1));

    loginToggle
        .selectedProperty()
        .addListener(
            (obs, wasSelected, isSelected) -> {
              if (isSelected) switchToLogin();
            });
    registerToggle
        .selectedProperty()
        .addListener(
            (obs, wasSelected, isSelected) -> {
              if (isSelected) switchToRegister();
            });

    loginButton.setOnAction(e -> handleLogin());
    passwordField.setOnAction(e -> handleLogin());
    registerButton.setOnAction(e -> handleRegister());
    regConfirmField.setOnAction(e -> handleRegister());

    updateLocale();
    switchToLogin();
  }

  /** Switches the UI to show the login form and clears any displayed error. */
  private void switchToLogin() {
    loginForm.setVisible(true);
    loginForm.setManaged(true);
    registerForm.setVisible(false);
    registerForm.setManaged(false);
    errorLabel.setText("");
  }

  /** Switches the UI to show the registration form and clears any displayed error. */
  private void switchToRegister() {
    loginForm.setVisible(false);
    loginForm.setManaged(false);
    registerForm.setVisible(true);
    registerForm.setManaged(true);
    errorLabel.setText("");
  }

  /**
   * Sets the connection module used to communicate with the server.
   *
   * @param connection the client-server connection module
   */
  public void setConnection(ClientConnectionModule connection) {
    this.connection = connection;
  }

  /**
   * Sets the stage for this authentication window.
   *
   * @param stage the JavaFX stage to control (close, set title, etc.)
   */
  public void setStage(Stage stage) {
    this.stage = stage;
    stage.xProperty().addListener((obs, old, val) -> languageMenu.hide());
    stage.yProperty().addListener((obs, old, val) -> languageMenu.hide());
    stage.widthProperty().addListener((obs, old, val) -> languageMenu.hide());
    stage.heightProperty().addListener((obs, old, val) -> languageMenu.hide());
  }

  /**
   * Registers a callback invoked when authentication succeeds.
   *
   * <p>The callback receives the login and password of the authenticated user, which can be used by
   * the caller for subsequent requests.
   *
   * @param callback the success callback (login, password)
   */
  public void setOnAuthSuccess(BiConsumer<String, String> callback) {
    this.onAuthSuccess = callback;
  }

  /**
   * Updates all UI labels and the window title to match the current locale.
   *
   * <p>Called during initialization and whenever the locale is changed via the language menu.
   */
  private void updateLocale() {
    LocaleManager lm = LocaleManager.getInstance();
    loginToggle.setText(lm.getString("auth.login.tab"));
    registerToggle.setText(lm.getString("auth.register.tab"));
    loginFieldLabel.setText(lm.getString("auth.login.label"));
    passwordFieldLabel.setText(lm.getString("auth.password.label"));
    loginButton.setText(lm.getString("auth.login.button"));
    regLoginFieldLabel.setText(lm.getString("auth.register.login.label"));
    regPasswordFieldLabel.setText(lm.getString("auth.register.password.label"));
    regConfirmFieldLabel.setText(lm.getString("auth.register.confirm.label"));
    registerButton.setText(lm.getString("auth.register.button"));
    languageLabel.setText(lm.getString("auth.language"));
    headerLabel.setText(lm.getString("auth.header"));
    if (stage != null) stage.setTitle(lm.getString("auth.title"));
  }

  /**
   * Handles the login button action.
   *
   * <p>Validates that the login and password fields are not empty, then sends a {@link
   * CommandType#LOGIN} request to the server asynchronously. On success the window is closed and
   * the {@link #onAuthSuccess} callback is invoked; on failure an inline error message is
   * displayed. Connection-level errors are shown in an {@link Alert} dialog.
   */
  private void handleLogin() {
    String login = loginField.getText().trim();
    String password = passwordField.getText();
    LocaleManager lm = LocaleManager.getInstance();

    if (login.isEmpty() || password.isEmpty()) {
      errorLabel.setText(lm.getString("auth.error.empty"));
      return;
    }

    errorLabel.setText("");
    setFormDisabled(true);

    CompletableFuture.supplyAsync(
            () ->
                connection.sendRequest(
                    new Request(CommandType.LOGIN, null, null, null, login, password)))
        .thenAccept(
            response ->
                Platform.runLater(
                    () -> {
                      setFormDisabled(false);
                      if (response != null && response.success()) {
                        if (onAuthSuccess != null) onAuthSuccess.accept(login, password);
                        if (stage != null) stage.close();
                      } else {
                        String msg = response != null ? response.message() : "no response";
                        errorLabel.setText(lm.getString("auth.error.failed", msg));
                      }
                    }))
        .exceptionally(
            error -> {
              Platform.runLater(
                  () -> {
                    setFormDisabled(false);
                    showError(lm.getString("auth.error.connection", error.getCause().getMessage()));
                  });
              return null;
            });
  }

  /**
   * Handles the register button action.
   *
   * <p>Validates that login and password are not empty and that the password confirmation matches,
   * then sends a {@link CommandType#REGISTER} request to the server asynchronously. On success the
   * window is closed and the {@link #onAuthSuccess} callback is invoked; on failure an inline error
   * message is displayed. Connection-level errors are shown in an {@link Alert} dialog.
   */
  private void handleRegister() {
    String login = regLoginField.getText().trim();
    String password = regPasswordField.getText();
    String confirm = regConfirmField.getText();

    LocaleManager lm = LocaleManager.getInstance();

    if (login.isEmpty() || password.isEmpty()) {
      errorLabel.setText(lm.getString("auth.error.empty"));
      return;
    }

    if (!password.equals(confirm)) {
      errorLabel.setText(lm.getString("auth.error.passwords.mismatch"));
      return;
    }

    errorLabel.setText("");
    setFormDisabled(true);

    CompletableFuture.supplyAsync(
            () ->
                connection.sendRequest(
                    new Request(CommandType.REGISTER, null, null, null, login, password)))
        .thenAccept(
            response ->
                Platform.runLater(
                    () -> {
                      setFormDisabled(false);
                      if (response != null && response.success()) {
                        if (onAuthSuccess != null) onAuthSuccess.accept(login, password);
                        if (stage != null) stage.close();
                      } else {
                        String msg = response != null ? response.message() : "no response";
                        errorLabel.setText(lm.getString("auth.error.failed", msg));
                      }
                    }))
        .exceptionally(
            error -> {
              Platform.runLater(
                  () -> {
                    setFormDisabled(false);
                    showError(lm.getString("auth.error.connection", error.getCause().getMessage()));
                  });
              return null;
            });
  }

  /**
   * Displays a system-level error message in an alert dialog.
   *
   * <p>Used for unexpected errors such as connection failures. Planned validation and
   * authentication errors are shown inline via {@link #errorLabel}.
   *
   * @param message the error text to show
   */
  private void showError(String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR, message);
    alert.initOwner(stage);
    alert.showAndWait();
  }

  /**
   * Enables or disables all form controls (text fields, buttons, language menu) and toggles the
   * visibility of the progress indicator for the currently active form.
   *
   * @param disabled {@code true} to disable the form (and show the spinner), {@code false} to
   *     re-enable it
   */
  private void setFormDisabled(boolean disabled) {
    loginButton.setDisable(disabled);
    registerButton.setDisable(disabled);
    loginField.setDisable(disabled);
    passwordField.setDisable(disabled);
    regLoginField.setDisable(disabled);
    regPasswordField.setDisable(disabled);
    regConfirmField.setDisable(disabled);
    languageMenu.setDisable(disabled);
    loginProgress.setVisible(disabled && loginToggle.isSelected());
    loginProgress.setManaged(disabled && loginToggle.isSelected());
    registerProgress.setVisible(disabled && registerToggle.isSelected());
    registerProgress.setManaged(disabled && registerToggle.isSelected());
  }
}
