package ru.app;

import java.io.PrintWriter;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.app.client.ClientConnectionModule;
import ru.app.ui.AuthController;
import ru.app.ui.MainController;
import ru.app.util.LocaleManager;

/**
 * Entry point for the client application.
 *
 * <p>Handles authentication via a JavaFX window ({@code auth.fxml}) and then opens the main
 * application window ({@code main.fxml}) with a table view, visualization canvas, and command
 * buttons. All server communication is handled via {@link ClientConnectionModule}.
 */
public class Client extends Application {
  private static final String HOST = "localhost";
  private static final int PORT = 8888;
  private static final int TIMEOUT_MS = 3000;
  private static final int MAX_RETRIES = 3;

  private static String login = null;
  private static String password = null;
  private static ClientConnectionModule clientConnection;
  private static PrintWriter out;

  /**
   * Application entry point. Connects to the server, executes the JavaFX application lifecycle, and
   * shuts down the connection on exit.
   *
   * @param args command-line arguments (not used)
   */
  public static void main(String[] args) {
    out = new PrintWriter(System.out, true);

    out.println("Client started. Connecting to " + HOST + ":" + PORT);

    try {
      clientConnection = new ClientConnectionModule(HOST, PORT, TIMEOUT_MS, MAX_RETRIES);
    } catch (Exception e) {
      out.println("Failed to connect: " + e.getMessage());
      return;
    }

    launch(args);

    if (login == null) {
      out.println("Authentication required. Exiting.");
    }

    clientConnection.close();
    out.println("Client shutdown complete.");
  }

  /**
   * Opens the authentication window, then on success creates and shows the main application window.
   *
   * <p>The auth window is shown modally; the method blocks until the user authenticates or closes
   * the window. If authentication succeeds, the primary stage is replaced with the main GUI scene
   * containing the product table, visualization canvas, and action toolbar.
   *
   * @param primaryStage the primary stage for this application (reused for the main window)
   */
  @Override
  public void start(Stage primaryStage) {
    try {
      FXMLLoader authLoader = new FXMLLoader(getClass().getResource("/fxml/auth.fxml"));
      authLoader.setResources(LocaleManager.getInstance().getBundle());
      Parent authRoot = authLoader.load();
      AuthController authController = authLoader.getController();

      Stage authStage = new Stage();
      Scene authScene = new Scene(authRoot);
      authScene.getStylesheets().add(getClass().getResource("/css/auth.css").toExternalForm());
      authStage.setScene(authScene);
      authStage.setWidth(700);
      authStage.setHeight(700);
      authStage.setResizable(true);
      authStage.setMinWidth(500);
      authStage.setMinHeight(500);

      authController.setConnection(clientConnection);
      authController.setStage(authStage);
      authController.setOnAuthSuccess(
          (user, pass) -> {
            login = user;
            password = pass;
            authStage.close();
          });

      authStage.setTitle(LocaleManager.getInstance().getString("auth.title"));
      authStage.showAndWait();

      if (login != null) {
        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        mainLoader.setResources(LocaleManager.getInstance().getBundle());
        Parent mainRoot = mainLoader.load();
        MainController mainController = mainLoader.getController();
        mainController.initData(clientConnection, login, password, primaryStage);

        primaryStage.setTitle(LocaleManager.getInstance().getString("main.title"));
        Scene mainScene = new Scene(mainRoot, 1200, 800);
        mainScene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
        primaryStage.setScene(mainScene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setOnCloseRequest(e -> clientConnection.close());
        primaryStage.show();
      }
    } catch (Exception e) {
      out.println("Failed to start UI:");
      e.printStackTrace(out);
    }
  }
}
