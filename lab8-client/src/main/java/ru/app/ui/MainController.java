package ru.app.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import ru.app.client.ClientConnectionModule;
import ru.app.network.CommandType;
import ru.app.network.Request;
import ru.app.object.Product;
import ru.app.object.UnitOfMeasure;
import ru.app.util.LocaleManager;

/**
 * Controller for the main application window shown after successful authentication.
 *
 * <p>Manages the product table, filter controls, visualisation canvas, and action toolbar. All
 * server communication is performed asynchronously via {@link ClientConnectionModule}. The
 * interface is fully localised through {@link LocaleManager} and supports live language switching.
 */
public class MainController {

  private final ObservableList<Product> productData = FXCollections.observableArrayList();
  private final ObservableList<Product> displayData = FXCollections.observableArrayList();
  private final List<TableColumn<Product, ?>> columns = new ArrayList<>();
  private final List<Function<Product, ?>> extractors = new ArrayList<>();
  @FXML private BorderPane rootPane;
  @FXML private HBox topBar;
  @FXML private Label userLabel;
  @FXML private Label languageLabel;
  @FXML private MenuButton languageMenu;
  @FXML private SplitPane splitPane;
  @FXML private HBox filterBar;
  @FXML private TextField filterField;
  @FXML private Label unitFilterLabel;
  @FXML private ComboBox<UnitOfMeasure> unitFilterCombo;
  @FXML private TableView<Product> productTable;
  @FXML private StackPane canvasContainer;
  @FXML private Canvas visualizationCanvas;
  @FXML private HBox actionBar;
  @FXML private Button addButton;
  @FXML private Button addIfMinButton;
  @FXML private Button editButton;
  @FXML private Button deleteButton;
  @FXML private Button clearButton;
  @FXML private Button removeFirstButton;
  @FXML private Button infoButton;
  @FXML private Button sumPriceButton;
  @FXML private Button avgPriceButton;
  @FXML private Button scriptButton;

  private ClientConnectionModule connection;
  private String login;
  private String password;
  private Stage stage;
  private CanvasRenderer canvasRenderer;
  private Timeline refreshTimeline;
  private Product pendingEditProduct;

  /**
   * Initializes the controller after FXML loading. Configures the language menu, locale listener,
   * table columns, filter controls, and button actions. Applies the current locale to all UI
   * elements.
   */
  @FXML
  public void initialize() {
    LocaleManager lm = LocaleManager.getInstance();

    setupLanguageMenu(lm);
    setupLocaleListener(lm);
    setupTableColumns();
    setupFilter();
    setupDisplayList();
    bindButtons();

    updateLocale();
  }

  /**
   * Sets the connection module, user credentials, and stage reference, then loads the initial
   * product list from the server.
   *
   * @param connection the client-server connection module
   * @param login authenticated user login
   * @param password authenticated user password
   * @param stage the primary stage for dialogs and window control
   */
  public void initData(
      ClientConnectionModule connection, String login, String password, Stage stage) {
    this.connection = connection;
    this.login = login;
    this.password = password;
    this.stage = stage;

    userLabel.setText(LocaleManager.getInstance().getString("main.user.label", login));

    canvasRenderer = new CanvasRenderer(visualizationCanvas);
    canvasRenderer.setOnMultipleProductsClick(this::showProductSelectionDialog);
    canvasRenderer.setOnEditRequest(
        () -> {
          Product clicked = canvasRenderer.getLastClickedProduct();
          if (clicked == null) return;
          if (login.equals(clicked.createdBy())) {
            pendingEditProduct = clicked;
            Platform.runLater(this::handleEdit);
          } else {
            Platform.runLater(() -> showProductInfoDialog(clicked));
          }
        });
    canvasRenderer.setOnTableSelect(
        product -> {
          if (product == null) productTable.getSelectionModel().clearSelection();
          else productTable.getSelectionModel().select(product);
        });

    productTable
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, old, val) -> canvasRenderer.setSelectedProduct(val));

    visualizationCanvas.widthProperty().bind(canvasContainer.widthProperty().subtract(4));
    visualizationCanvas.heightProperty().bind(canvasContainer.heightProperty().subtract(4));
    visualizationCanvas.widthProperty().addListener((obs, o, n) -> rerenderCanvas());
    visualizationCanvas.heightProperty().addListener((obs, o, n) -> rerenderCanvas());

    loadProducts();
    startRefreshTimer();
  }

  /**
   * Populates the language menu with all available locales.
   *
   * @param lm the locale manager instance
   */
  private void setupLanguageMenu(LocaleManager lm) {
    for (Locale locale : LocaleManager.AVAILABLE_LOCALES) {
      String nativeName = locale.getDisplayName(locale);
      MenuItem item =
          new MenuItem(Character.toUpperCase(nativeName.charAt(0)) + nativeName.substring(1));
      item.setUserData(locale);
      item.setOnAction(
          e -> {
            Locale selected = (Locale) item.getUserData();
            if (!selected.equals(lm.getCurrentLocale())) lm.setLocale(selected);
          });
      languageMenu.getItems().add(item);
    }

    Locale cur = lm.getCurrentLocale();
    String curName = cur.getDisplayName(cur);
    languageMenu.setText(Character.toUpperCase(curName.charAt(0)) + curName.substring(1));
  }

  /**
   * Attaches a listener to locale changes that updates all UI text and the language menu label.
   *
   * @param lm the locale manager instance
   */
  private void setupLocaleListener(LocaleManager lm) {
    lm.localeProperty()
        .addListener(
            (obs, old, val) -> {
              updateLocale();
              String name = val.getDisplayName(val);
              languageMenu.setText(Character.toUpperCase(name.charAt(0)) + name.substring(1));
            });
  }

  /**
   * Refreshes all user-visible text to match the current locale. Called during initialization and
   * whenever the locale is changed.
   */
  private void updateLocale() {
    LocaleManager lm = LocaleManager.getInstance();

    if (login != null) userLabel.setText(lm.getString("main.user.label", login));
    languageLabel.setText(lm.getString("auth.language"));
    filterField.setPromptText(lm.getString("main.filter.placeholder"));
    unitFilterLabel.setText(lm.getString("main.filter.unit"));
    unitFilterCombo.setButtonCell(createFilterCell());
    if (stage != null) stage.setTitle(lm.getString("main.title"));

    addButton.setText(lm.getString("main.button.add"));
    addIfMinButton.setText(lm.getString("main.button.addIfMin"));
    editButton.setText(lm.getString("main.button.edit"));
    deleteButton.setText(lm.getString("main.button.delete"));
    clearButton.setText(lm.getString("main.button.clear"));
    removeFirstButton.setText(lm.getString("main.button.removeFirst"));
    infoButton.setText(lm.getString("main.button.info"));
    sumPriceButton.setText(lm.getString("main.button.sum"));
    avgPriceButton.setText(lm.getString("main.button.avg"));
    scriptButton.setText(lm.getString("main.button.script"));

    updateTableHeaders();
  }

  /** Updates the header text of all table columns to match the current locale. */
  private void updateTableHeaders() {
    LocaleManager lm = LocaleManager.getInstance();

    if (columns.isEmpty()) return;

    String[] keys = {
      "main.table.id", "main.table.name", "main.table.coordinatesX",
      "main.table.coordinatesY", "main.table.price", "main.table.partNumber",
      "main.table.unitOfMeasure", "main.table.ownerName", "main.table.ownerBirthday",
      "main.table.ownerPassport", "main.table.locationX", "main.table.locationY",
      "main.table.locationZ", "main.table.locationName", "main.table.creationDate",
      "main.table.createdBy"
    };

    for (int i = 0; i < columns.size() && i < keys.length; i++)
      columns.get(i).setText(lm.getString(keys[i]));
  }

  /**
   * Configures the table columns for every Product field, including nested fields of Coordinates,
   * Person, Location, and the creation date and creator. Registers a double-click handler for
   * editing.
   */
  @SuppressWarnings("unchecked")
  private void setupTableColumns() {
    productTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    productTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

    columns.clear();
    extractors.clear();

    columns.add(createColumn("id", Product::id, Integer.class));
    columns.add(createColumn("name", Product::name, String.class));
    columns.add(createColumn("coordX", p -> p.coordinates().x(), Integer.class));
    columns.add(createColumn("coordY", p -> p.coordinates().y(), Float.class));
    columns.add(createColumn("price", p -> p.price(), Float.class));
    columns.add(createColumn("partNumber", p -> p.partNumber(), String.class));
    columns.add(createColumn("unitOfMeasure", p -> p.unitOfMeasure(), UnitOfMeasure.class));
    columns.add(createColumn("ownerName", p -> p.owner().name(), String.class));
    columns.add(createColumn("ownerBirthday", p -> p.owner().birthday(), Date.class));
    columns.add(createColumn("ownerPassport", p -> p.owner().passportID(), String.class));
    columns.add(createColumn("locX", p -> p.owner().location().x(), Float.class));
    columns.add(createColumn("locY", p -> p.owner().location().y(), Integer.class));
    columns.add(createColumn("locZ", p -> p.owner().location().z(), Long.class));
    columns.add(createColumn("locName", p -> p.owner().location().name(), String.class));
    columns.add(createColumn("creationDate", p -> p.creationDate(), ZonedDateTime.class));
    columns.add(createColumn("createdBy", p -> p.createdBy(), String.class));

    productTable
        .getColumns()
        .setAll((TableColumn<Product, ?>[]) columns.toArray(new TableColumn[0]));

    productTable.setOnMouseClicked(this::onTableDoubleClick);
  }

  /**
   * Creates a single table column with a value extractor and locale-aware cell formatting. Dates
   * ({@code ZonedDateTime}, {@code Date}) and numbers ({@code Float}, {@code Integer}, etc.) are
   * formatted according to the current locale.
   */
  private <T> TableColumn<Product, T> createColumn(
      String id, Function<Product, T> extractor, Class<T> type) {
    extractors.add(extractor);
    TableColumn<Product, T> col = new TableColumn<>(id);
    col.setCellValueFactory(
        cellData -> {
          T value = extractor.apply(cellData.getValue());
          return new SimpleObjectProperty<>(value);
        });
    col.setCellFactory(
        column ->
            new TableCell<Product, T>() {
              @Override
              protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setText(null);
                  return;
                }
                LocaleManager lm = LocaleManager.getInstance();
                if (item instanceof ZonedDateTime zdt)
                  setText(lm.getDateTimeFormatter().format(zdt));
                else if (item instanceof Date date)
                  setText(
                      lm.getDateFormatter()
                          .format(
                              date.toInstant()
                                  .atZone(java.time.ZoneId.systemDefault())
                                  .toLocalDate()));
                else if (item instanceof UnitOfMeasure) setText(item.toString());
                else if (item instanceof Float f) {
                  if (Float.isNaN(f)) setText(null);
                  else setText(lm.getNumberFormat().format(f));
                } else if (item instanceof Number n) setText(lm.getNumberFormat().format(n));
                else setText(item.toString());
              }
            });
    col.sortTypeProperty().addListener((obs, o, n) -> applyFilterAndSort());
    col.setMinWidth(60);
    col.setPrefWidth(90);
    col.setSortable(true);
    col.setReorderable(true);
    return col;
  }

  /**
   * Initialises the filter controls: adds a null item to the unit ComboBox for "no filter", then
   * attaches listeners to both the text field and the ComboBox to re-apply the filter on change.
   */
  private void setupFilter() {
    unitFilterCombo.getItems().add(null);
    unitFilterCombo.getItems().addAll(UnitOfMeasure.values());
    unitFilterCombo.setCellFactory(lv -> createFilterCell());
    unitFilterCombo.setButtonCell(createFilterCell());
    unitFilterCombo
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, old, val) -> applyFilterAndSort());
    filterField.textProperty().addListener((obs, old, val) -> applyFilterAndSort());
  }

  private ListCell<UnitOfMeasure> createFilterCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(UnitOfMeasure item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null)
          setText(LocaleManager.getInstance().getString("main.filter.all"));
        else setText(item.toString());
      }
    };
  }

  /** Configures the table to use a manually managed display list sorted via Streams API. */
  private void setupDisplayList() {
    productTable.setItems(displayData);
    productTable
        .getSortOrder()
        .addListener(
            (ListChangeListener<TableColumn<Product, ?>>)
                c -> {
                  applyFilterAndSort();
                });
  }

  /**
   * Filters and sorts products using the Streams API, then updates the display list. Filtering
   * checks name, part number, owner name, and creator (case-insensitive). Sorting mirrors the table
   * column sort order.
   */
  private void applyFilterAndSort() {
    String text = filterField.getText() != null ? filterField.getText().toLowerCase() : "";
    UnitOfMeasure uom = unitFilterCombo.getSelectionModel().getSelectedItem();

    List<Product> result =
        productData.stream()
            .filter(p -> matchesFilter(p, text, uom))
            .sorted(buildComparator())
            .collect(Collectors.toList());

    displayData.setAll(result);
  }

  private boolean matchesFilter(Product p, String text, UnitOfMeasure uom) {
    if (!text.isEmpty()) {
      boolean matches =
          p.name().toLowerCase().contains(text)
              || p.partNumber().toLowerCase().contains(text)
              || p.owner().name().toLowerCase().contains(text)
              || (p.createdBy() != null && p.createdBy().toLowerCase().contains(text));
      if (!matches) return false;
    }
    if (uom != null && p.unitOfMeasure() != uom) return false;
    return true;
  }

  @SuppressWarnings("unchecked")
  private Comparator<Product> buildComparator() {
    return productTable.getSortOrder().stream()
        .map(
            col -> {
              int idx = productTable.getColumns().indexOf(col);
              if (idx < 0 || idx >= extractors.size()) return (Comparator<Product>) (a, b) -> 0;
              Function<Product, Object> ext = (Function<Product, Object>) extractors.get(idx);
              boolean asc = col.getSortType() == TableColumn.SortType.ASCENDING;
              return (Comparator<Product>)
                  (a, b) -> {
                    Object v1 = ext.apply(a);
                    Object v2 = ext.apply(b);
                    if (v1 == null && v2 == null) return 0;
                    if (v1 == null) return asc ? -1 : 1;
                    if (v2 == null) return asc ? 1 : -1;
                    int cmp = ((Comparable<Object>) v1).compareTo(v2);
                    return asc ? cmp : -cmp;
                  };
            })
        .reduce(Comparator::thenComparing)
        .orElse((a, b) -> 0);
  }

  /**
   * Wires all action toolbar buttons to their handler methods and disables the Edit and Delete
   * buttons when no row is selected.
   */
  private void bindButtons() {
    addButton.setOnAction(e -> handleAdd());
    addIfMinButton.setOnAction(e -> handleAddIfMin());
    editButton.setOnAction(e -> handleEdit());
    deleteButton.setOnAction(e -> handleDelete());
    clearButton.setOnAction(e -> handleClear());
    removeFirstButton.setOnAction(e -> handleRemoveFirst());
    infoButton.setOnAction(e -> handleInfo());
    sumPriceButton.setOnAction(e -> handleSumPrice());
    avgPriceButton.setOnAction(e -> handleAvgPrice());
    scriptButton.setOnAction(e -> handleExecuteScript());

    editButton
        .disableProperty()
        .bind(productTable.getSelectionModel().selectedItemProperty().isNull());
    deleteButton
        .disableProperty()
        .bind(productTable.getSelectionModel().selectedItemProperty().isNull());
  }

  /**
   * Handles double-click on a table row — triggers edit for the selected product.
   *
   * @param event the mouse event
   */
  private void onTableDoubleClick(MouseEvent event) {
    if (event.getClickCount() == 2) handleEdit();
  }

  private void startRefreshTimer() {
    refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> loadProducts()));
    refreshTimeline.setCycleCount(Timeline.INDEFINITE);
    refreshTimeline.play();
  }

  private void rerenderCanvas() {
    if (canvasRenderer != null && !productData.isEmpty())
      canvasRenderer.render(List.copyOf(productData));
  }

  /**
   * Sends a SHOW request to the server and replaces the local product list with the response. Runs
   * the network call asynchronously and updates the UI on the JavaFX thread.
   */
  private void loadProducts() {
    CompletableFuture.supplyAsync(
            () ->
                connection.sendRequest(
                    new Request(CommandType.SHOW, null, null, null, login, password)))
        .thenAccept(
            response ->
                Platform.runLater(
                    () -> {
                      if (response != null && response.success() && response.data() != null) {
                        List<Product> products =
                            response.data().stream()
                                .filter(obj -> obj instanceof Product)
                                .map(obj -> (Product) obj)
                                .collect(Collectors.toList());
                        productData.setAll(products);
                        applyFilterAndSort();
                        if (canvasRenderer != null) canvasRenderer.render(products);
                      }
                    }));
  }

  private void handleAdd() {
    ProductFormDialog dialog = new ProductFormDialog(stage, null);
    Product product = dialog.showAndWait();
    if (product == null) return;
    CompletableFuture.supplyAsync(
            () ->
                connection.sendRequest(
                    new Request(CommandType.ADD, product, null, null, login, password)))
        .thenAccept(
            resp ->
                Platform.runLater(
                    () -> {
                      if (resp != null && resp.success()) loadProducts();
                      else
                        showErrorDialog(
                            LocaleManager.getInstance()
                                .getString(
                                    "product.error.saveFailed",
                                    resp != null ? resp.message() : "no response"));
                    }));
  }

  private void handleAddIfMin() {
    ProductFormDialog dialog = new ProductFormDialog(stage, null);
    Product product = dialog.showAndWait();
    if (product == null) return;
    CompletableFuture.supplyAsync(
            () ->
                connection.sendRequest(
                    new Request(CommandType.ADD_IF_MIN, product, null, null, login, password)))
        .thenAccept(
            resp ->
                Platform.runLater(
                    () -> {
                      if (resp != null && resp.success()) loadProducts();
                      else
                        showErrorDialog(
                            LocaleManager.getInstance()
                                .getString(
                                    "product.error.saveFailed",
                                    resp != null ? resp.message() : "no response"));
                    }));
  }

  private void handleEdit() {
    Product selected = pendingEditProduct;
    pendingEditProduct = null;
    if (selected == null) selected = productTable.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    Product productForEdit = selected;
    if (!login.equals(productForEdit.createdBy())) {
      showProductInfoDialog(productForEdit);
      return;
    }
    ProductFormDialog dialog = new ProductFormDialog(stage, productForEdit);
    Product updated = dialog.showAndWait();
    if (updated == null) return;
    CompletableFuture.supplyAsync(
            () ->
                connection.sendRequest(
                    new Request(
                        CommandType.UPDATE, updated, productForEdit.id(), null, login, password)))
        .thenAccept(
            resp ->
                Platform.runLater(
                    () -> {
                      if (resp != null && resp.success()) loadProducts();
                      else
                        showErrorDialog(
                            LocaleManager.getInstance()
                                .getString(
                                    "product.error.saveFailed",
                                    resp != null ? resp.message() : "no response"));
                    }));
  }

  /**
   * Deletes the selected product after user confirmation. Only the owner may delete their own
   * products. On success, refreshes the product list.
   */
  private void handleDelete() {
    Product selected = productTable.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    if (!login.equals(selected.createdBy())) {
      showInfoDialog(
          LocaleManager.getInstance().getString("main.button.delete"),
          LocaleManager.getInstance().getString("main.error.notOwnerDelete"));
      return;
    }
    LocaleManager lm = LocaleManager.getInstance();
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(lm.getString("main.button.delete"));
    alert.setHeaderText(lm.getString("main.confirm.delete", selected.id()));
    alert.initOwner(stage);

    alert
        .showAndWait()
        .ifPresent(
            response -> {
              if (response == ButtonType.OK)
                CompletableFuture.supplyAsync(
                        () ->
                            connection.sendRequest(
                                new Request(
                                    CommandType.REMOVE_BY_ID,
                                    null,
                                    selected.id(),
                                    null,
                                    login,
                                    password)))
                    .thenAccept(
                        resp ->
                            Platform.runLater(
                                () -> {
                                  if (resp != null && resp.success()) loadProducts();
                                  else
                                    showInfoDialog(
                                        LocaleManager.getInstance().getString("main.error.title"),
                                        resp != null
                                            ? resp.message()
                                            : LocaleManager.getInstance()
                                                .getString("main.error.noResponse"));
                                }));
            });
  }

  /**
   * Clears all products belonging to the current user after confirmation. Refreshes the product
   * list on success.
   */
  private void handleClear() {
    LocaleManager lm = LocaleManager.getInstance();
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(lm.getString("main.button.clear"));
    alert.setHeaderText(lm.getString("main.confirm.clear"));
    alert.initOwner(stage);

    alert
        .showAndWait()
        .ifPresent(
            response -> {
              if (response == ButtonType.OK)
                CompletableFuture.supplyAsync(
                        () ->
                            connection.sendRequest(
                                new Request(CommandType.CLEAR, null, null, null, login, password)))
                    .thenAccept(
                        resp ->
                            Platform.runLater(
                                () -> {
                                  if (resp != null && resp.success()) loadProducts();
                                  else
                                    showInfoDialog(
                                        LocaleManager.getInstance().getString("main.error.title"),
                                        resp != null
                                            ? resp.message()
                                            : LocaleManager.getInstance()
                                                .getString("main.error.noResponse"));
                                }));
            });
  }

  /**
   * Removes the first product (lowest ID) belonging to the current user. Refreshes the product list
   * on success.
   */
  private void handleRemoveFirst() {
    CompletableFuture.supplyAsync(
            () ->
                connection.sendRequest(
                    new Request(CommandType.REMOVE_FIRST, null, null, null, login, password)))
        .thenAccept(
            resp ->
                Platform.runLater(
                    () -> {
                      if (resp != null && resp.success()) loadProducts();
                      else
                        showInfoDialog(
                            LocaleManager.getInstance().getString("main.error.title"),
                            resp != null
                                ? resp.message()
                                : LocaleManager.getInstance().getString("main.error.noResponse"));
                    }));
  }

  /** Fetches and displays collection information (type, size, creation date) from the server. */
  private void handleInfo() {
    CompletableFuture.supplyAsync(
            () ->
                connection.sendRequest(
                    new Request(CommandType.INFO, null, null, null, login, password)))
        .thenAccept(
            resp ->
                Platform.runLater(
                    () -> {
                      if (resp != null)
                        showInfoDialog(
                            LocaleManager.getInstance().getString("main.button.info"),
                            resp.message());
                    }));
  }

  /** Fetches and displays the sum of all product prices from the server. */
  private void handleSumPrice() {
    CompletableFuture.supplyAsync(
            () ->
                connection.sendRequest(
                    new Request(CommandType.SUM_OF_PRICE, null, null, null, login, password)))
        .thenAccept(
            resp ->
                Platform.runLater(
                    () -> {
                      if (resp != null)
                        showInfoDialog(
                            LocaleManager.getInstance().getString("main.button.sum"),
                            resp.message());
                    }));
  }

  /** Fetches and displays the average product price from the server. */
  private void handleAvgPrice() {
    CompletableFuture.supplyAsync(
            () ->
                connection.sendRequest(
                    new Request(CommandType.AVERAGE_OF_PRICE, null, null, null, login, password)))
        .thenAccept(
            resp ->
                Platform.runLater(
                    () -> {
                      if (resp != null)
                        showInfoDialog(
                            LocaleManager.getInstance().getString("main.button.avg"),
                            resp.message());
                    }));
  }

  private void handleExecuteScript() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle(LocaleManager.getInstance().getString("main.button.script"));
    fileChooser
        .getExtensionFilters()
        .add(
            new FileChooser.ExtensionFilter("Script files (*.txt, *.script)", "*.txt", "*.script"));

    File file = fileChooser.showOpenDialog(stage);
    if (file == null) return;

    LocaleManager lm = LocaleManager.getInstance();
    List<String> results = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;

        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toUpperCase();

        CommandType type;
        try {
          type = CommandType.valueOf(cmd);
        } catch (IllegalArgumentException e) {
          results.add(lm.getString("main.script.unknownCmd", cmd));
          continue;
        }

        if (type == CommandType.ADD || type == CommandType.UPDATE) {
          results.add("SKIP " + cmd + " — " + lm.getString("main.script.skipReason.addUpdate"));
          continue;
        }

        if (type == CommandType.REGISTER || type == CommandType.LOGIN) {
          results.add("SKIP " + cmd + " — " + lm.getString("main.script.skipReason.auth"));
          continue;
        }

        if (type == CommandType.EXECUTE_SCRIPT || type == CommandType.EXIT) {
          results.add("SKIP " + cmd + " — " + lm.getString("main.script.skipReason.unavailable"));
          continue;
        }

        String arg = parts.length > 1 ? parts[1] : null;
        Request request = new Request(type, null, null, arg, login, password);
        var resp = connection.sendRequest(request);
        if (resp != null) {
          results.add(
              resp.success()
                  ? lm.getString("main.script.resultOk", cmd, resp.message())
                  : lm.getString("main.script.resultFail", cmd, resp.message()));
        } else {
          results.add(lm.getString("main.script.noResponse"));
        }
      }
    } catch (IOException e) {
      showErrorDialog(lm.getString("main.script.readError", e.getMessage()));
      return;
    }

    StringBuilder sb = new StringBuilder();
    for (String r : results) sb.append(r).append("\n");
    showInfoDialog(lm.getString("main.button.script"), sb.toString());
  }

  /**
   * Shows a simple information dialog with the given title and message.
   *
   * @param title dialog title
   * @param message dialog content text
   */
  private void showProductInfoDialog(Product p) {
    if (p == null) return;
    LocaleManager lm = LocaleManager.getInstance();

    Label content = new Label();
    content.setWrapText(true);
    StringBuilder sb = new StringBuilder();
    sb.append("ID: ").append(p.id()).append("\n");
    sb.append(lm.getString("main.table.name")).append(": ").append(p.name()).append("\n");
    sb.append(lm.getString("main.table.coordinatesX"))
        .append(": ")
        .append(p.coordinates().x())
        .append("\n");
    sb.append(lm.getString("main.table.coordinatesY"))
        .append(": ")
        .append(p.coordinates().y())
        .append("\n");
    if (p.price() != null)
      sb.append(lm.getString("main.table.price"))
          .append(": ")
          .append(lm.getNumberFormat().format(p.price()))
          .append("\n");
    sb.append(lm.getString("main.table.partNumber"))
        .append(": ")
        .append(p.partNumber())
        .append("\n");
    sb.append(lm.getString("main.table.unitOfMeasure"))
        .append(": ")
        .append(p.unitOfMeasure())
        .append("\n");
    sb.append(lm.getString("main.table.ownerName"))
        .append(": ")
        .append(p.owner().name())
        .append("\n");
    if (p.owner().birthday() != null)
      sb.append(lm.getString("main.table.ownerBirthday"))
          .append(": ")
          .append(
              lm.getDateFormatter()
                  .format(
                      p.owner()
                          .birthday()
                          .toInstant()
                          .atZone(java.time.ZoneId.systemDefault())
                          .toLocalDate()))
          .append("\n");
    if (p.owner().passportID() != null && !p.owner().passportID().isEmpty())
      sb.append(lm.getString("main.table.ownerPassport"))
          .append(": ")
          .append(p.owner().passportID())
          .append("\n");
    sb.append(lm.getString("main.table.locationX"))
        .append(": ")
        .append(lm.getNumberFormat().format(p.owner().location().x()))
        .append("\n");
    sb.append(lm.getString("main.table.locationY"))
        .append(": ")
        .append(p.owner().location().y())
        .append("\n");
    sb.append(lm.getString("main.table.locationZ"))
        .append(": ")
        .append(p.owner().location().z())
        .append("\n");
    sb.append(lm.getString("main.table.locationName"))
        .append(": ")
        .append(p.owner().location().name())
        .append("\n");
    sb.append(lm.getString("main.table.creationDate"))
        .append(": ")
        .append(lm.getDateTimeFormatter().format(p.creationDate()))
        .append("\n");
    sb.append(lm.getString("main.table.createdBy")).append(": ").append(p.createdBy());
    content.setText(sb.toString());

    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.setTitle(lm.getString("main.button.info") + " — #" + p.id());
    dialog.initOwner(stage);
    dialog
        .getDialogPane()
        .getStylesheets()
        .add(getClass().getResource("/css/main.css").toExternalForm());
    dialog.getDialogPane().setContent(content);
    dialog.getDialogPane().setPrefSize(460, 380);
    dialog.getDialogPane().setMinSize(300, 250);
    dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
    dialog.setResizable(true);
    dialog.showAndWait();
  }

  private void showProductSelectionDialog(List<Product> candidates) {
    LocaleManager lm = LocaleManager.getInstance();
    ListView<Product> listView = new ListView<>();
    listView.setItems(FXCollections.observableArrayList(candidates));
    listView.setCellFactory(
        lv ->
            new javafx.scene.control.ListCell<>() {
              @Override
              protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText("ID: " + item.id() + " | " + item.name() + " | " + item.createdBy());
              }
            });

    Dialog<Product> dialog = new Dialog<>();
    dialog.setTitle(lm.getString("main.dialog.selectProduct"));
    dialog.initOwner(stage);
    dialog
        .getDialogPane()
        .getStylesheets()
        .add(getClass().getResource("/css/main.css").toExternalForm());
    dialog.getDialogPane().setPrefSize(380, 140);
    dialog.getDialogPane().setPadding(new Insets(2, 4, 2, 4));
    dialog.setResizable(true);

    dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
    Node cancelBtnNode = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
    cancelBtnNode.setManaged(false);
    cancelBtnNode.setVisible(false);

    Button cancelBtn = new Button(lm.getString("main.dialog.cancel"));
    cancelBtn.getStyleClass().add("button-secondary");

    Button selectBtn = new Button(lm.getString("main.dialog.select"));
    selectBtn.getStyleClass().add("button-secondary");
    selectBtn.setDefaultButton(true);

    Button editInfoBtn = new Button(lm.getString("main.button.edit"));
    editInfoBtn.getStyleClass().add("button-secondary");

    HBox btnBar = new HBox(4, cancelBtn, selectBtn, editInfoBtn);
    btnBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
    VBox content = new VBox(4, listView, btnBar);
    content.setFillWidth(true);
    VBox.setVgrow(listView, javafx.scene.layout.Priority.ALWAYS);
    dialog.getDialogPane().setContent(content);

    cancelBtn.setOnAction(e -> dialog.close());

    selectBtn.setOnAction(
        e -> {
          Product selected = listView.getSelectionModel().getSelectedItem();
          if (selected != null) {
            canvasRenderer.setSelectedProduct(selected);
            productTable.getSelectionModel().select(selected);
            dialog.close();
          }
        });

    editInfoBtn.setOnAction(
        e -> {
          Product selected = listView.getSelectionModel().getSelectedItem();
          if (selected == null) return;
          Product fp = selected;
          dialog.close();
          Platform.runLater(
              () -> {
                if (login.equals(fp.createdBy())) {
                  pendingEditProduct = fp;
                  handleEdit();
                } else {
                  showProductInfoDialog(fp);
                }
              });
        });

    listView.setOnMouseClicked(
        ev -> {
          if (ev.getClickCount() == 2) {
            Product selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            Product fp = selected;
            dialog.close();
            Platform.runLater(
                () -> {
                  if (login.equals(fp.createdBy())) {
                    pendingEditProduct = fp;
                    handleEdit();
                  } else {
                    showProductInfoDialog(fp);
                  }
                });
          }
        });

    dialog.showAndWait();
  }

  private void showInfoDialog(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.initOwner(stage);
    alert
        .getDialogPane()
        .getStylesheets()
        .add(getClass().getResource("/css/main.css").toExternalForm());
    alert.setResizable(true);
    alert.showAndWait();
  }

  private void showErrorDialog(String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(LocaleManager.getInstance().getString("main.error.title"));
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.initOwner(stage);
    alert
        .getDialogPane()
        .getStylesheets()
        .add(getClass().getResource("/css/main.css").toExternalForm());
    alert.setResizable(true);
    alert.showAndWait();
  }
}
