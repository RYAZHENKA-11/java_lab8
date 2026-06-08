package ru.app.ui;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ru.app.object.Coordinates;
import ru.app.object.Location;
import ru.app.object.Person;
import ru.app.object.Product;
import ru.app.object.UnitOfMeasure;
import ru.app.util.LocaleManager;

public class ProductFormDialog {

  private static final int MAX_INPUT_LENGTH = 10000;

  private final Stage dialogStage;
  private final boolean editMode;
  private final Product original;

  private TextField nameField;
  private TextField coordXField;
  private TextField coordYField;
  private TextField priceField;
  private TextField partNumberField;
  private ComboBox<UnitOfMeasure> uomCombo;
  private TextField ownerNameField;
  private DatePicker birthdayPicker;
  private TextField passportField;
  private TextField locXField;
  private TextField locYField;
  private TextField locZField;
  private TextField locNameField;
  private Label errorLabel;

  private Product result;

  public ProductFormDialog(Stage owner, Product product) {
    this.original = product;
    this.editMode = product != null;
    this.dialogStage = new Stage();
    this.dialogStage.initModality(Modality.WINDOW_MODAL);
    this.dialogStage.initOwner(owner);
    buildUI();
    if (editMode) populateFields(product);
  }

  public Product showAndWait() {
    dialogStage.showAndWait();
    return result;
  }

  private void buildUI() {
    LocaleManager lm = LocaleManager.getInstance();

    VBox root = new VBox();
    root.getStyleClass().add("dialog-root");

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setPadding(new Insets(0, 0, 8, 0));
    ColumnConstraints labelCol = new ColumnConstraints();
    labelCol.setPrefWidth(120);
    ColumnConstraints fieldCol = new ColumnConstraints();
    fieldCol.setHgrow(Priority.ALWAYS);
    grid.getColumnConstraints().addAll(labelCol, fieldCol);

    int row = 0;

    nameField = new TextField();
    addField(grid, row++, lm.getString("product.field.name") + " *", nameField);

    coordXField = new TextField();
    addField(grid, row++, lm.getString("product.field.coordX") + " *", coordXField);

    coordYField = new TextField();
    addField(grid, row++, lm.getString("product.field.coordY") + " *", coordYField);

    priceField = new TextField();
    priceField.setPromptText(lm.getString("product.hint.price"));
    addField(grid, row++, lm.getString("product.field.price"), priceField);

    partNumberField = new TextField();
    partNumberField.setPromptText(lm.getString("product.hint.partNumber"));
    addField(grid, row++, lm.getString("product.field.partNumber") + " *", partNumberField);

    uomCombo = new ComboBox<>();
    uomCombo.getItems().addAll(UnitOfMeasure.values());
    uomCombo.setMaxWidth(Double.MAX_VALUE);
    addField(grid, row++, lm.getString("product.field.unitOfMeasure") + " *", uomCombo);

    ownerNameField = new TextField();
    addField(grid, row++, lm.getString("product.field.ownerName") + " *", ownerNameField);

    birthdayPicker = new DatePicker();
    birthdayPicker.setMaxWidth(Double.MAX_VALUE);
    birthdayPicker
        .showingProperty()
        .addListener(
            (obs, was, now) -> {
              if (!now) return;
              javafx.application.Platform.runLater(
                  () -> {
                    String css = getClass().getResource("/css/main.css").toExternalForm();
                    for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
                      if (w instanceof javafx.stage.PopupWindow && w.getScene() != null) {
                        if (!w.getScene().getStylesheets().contains(css))
                          w.getScene().getStylesheets().add(css);
                      }
                    }
                  });
            });
    addField(grid, row++, lm.getString("product.field.ownerBirthday"), birthdayPicker);

    passportField = new TextField();
    addField(grid, row++, lm.getString("product.field.ownerPassport"), passportField);

    locXField = new TextField();
    addField(grid, row++, lm.getString("product.field.locX") + " *", locXField);

    locYField = new TextField();
    addField(grid, row++, lm.getString("product.field.locY") + " *", locYField);

    locZField = new TextField();
    addField(grid, row++, lm.getString("product.field.locZ") + " *", locZField);

    locNameField = new TextField();
    locNameField.setPromptText(lm.getString("product.hint.locName"));
    addField(grid, row++, lm.getString("product.field.locName") + " *", locNameField);

    errorLabel = new Label();
    errorLabel.getStyleClass().add("dialog-error");
    errorLabel.setMaxWidth(Double.MAX_VALUE);
    errorLabel.setVisible(false);

    HBox buttonBar = new HBox();
    buttonBar.getStyleClass().add("dialog-button-bar");
    Button saveButton = new Button(lm.getString("product.dialog.save"));
    saveButton.getStyleClass().add("button-primary");
    saveButton.setOnAction(e -> handleSave());

    Button cancelButton = new Button(lm.getString("product.dialog.cancel"));
    cancelButton.getStyleClass().add("button-secondary");
    cancelButton.setOnAction(e -> dialogStage.close());

    buttonBar.getChildren().addAll(saveButton, cancelButton);

    VBox.setVgrow(grid, Priority.ALWAYS);
    root.getChildren().addAll(grid, errorLabel, buttonBar);

    Scene scene = new Scene(root, 420, 620);
    scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
    dialogStage.setScene(scene);
    dialogStage.setResizable(true);
    dialogStage.setMinWidth(380);
    dialogStage.setMinHeight(480);

    lm.localeProperty()
        .addListener(
            (obs, old, val) -> {
              updateLocale(lm);
              saveButton.setText(lm.getString("product.dialog.save"));
              cancelButton.setText(lm.getString("product.dialog.cancel"));
              errorLabel.setVisible(false);
            });
  }

  private void addField(GridPane grid, int row, String labelText, javafx.scene.Node field) {
    Label label = new Label(labelText);
    label.getStyleClass().add("field-label");
    GridPane.setFillWidth(field, true);
    if (field instanceof TextField tf) {
      tf.setOnAction(e -> handleSave());
    } else if (field instanceof DatePicker dp) {
      dp.setOnAction(e -> handleSave());
    }
    grid.add(label, 0, row);
    grid.add(field, 1, row);
    GridPane.setHgrow(field, Priority.ALWAYS);
  }

  private void updateLocale(LocaleManager lm) {
    String[] labels = {
      lm.getString("product.field.name") + " *",
      lm.getString("product.field.coordX") + " *",
      lm.getString("product.field.coordY") + " *",
      lm.getString("product.field.price"),
      lm.getString("product.field.partNumber") + " *",
      lm.getString("product.field.unitOfMeasure") + " *",
      lm.getString("product.field.ownerName") + " *",
      lm.getString("product.field.ownerBirthday"),
      lm.getString("product.field.ownerPassport"),
      lm.getString("product.field.locX") + " *",
      lm.getString("product.field.locY") + " *",
      lm.getString("product.field.locZ") + " *",
      lm.getString("product.field.locName") + " *"
    };
    GridPane grid = (GridPane) nameField.getParent();
    for (int i = 0; i < labels.length; i++) {
      javafx.scene.Node node = grid.getChildren().get(i * 2);
      if (node instanceof Label l) l.setText(labels[i]);
    }
    dialogStage.setTitle(
        editMode ? lm.getString("product.dialog.edit") : lm.getString("product.dialog.add"));

    partNumberField.setPromptText(lm.getString("product.hint.partNumber"));
    locNameField.setPromptText(lm.getString("product.hint.locName"));
    priceField.setPromptText(lm.getString("product.hint.price"));
  }

  private void populateFields(Product p) {
    nameField.setText(p.name());
    coordXField.setText(String.valueOf(p.coordinates().x()));
    coordYField.setText(String.valueOf(p.coordinates().y()));
    if (p.price() != null) priceField.setText(String.valueOf(p.price()));
    partNumberField.setText(p.partNumber());
    uomCombo.setValue(p.unitOfMeasure());
    ownerNameField.setText(p.owner().name());
    if (p.owner().birthday() != null) {
      birthdayPicker.setValue(
          p.owner().birthday().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }
    if (p.owner().passportID() != null && !p.owner().passportID().isEmpty())
      passportField.setText(p.owner().passportID());
    locXField.setText(String.valueOf(p.owner().location().x()));
    locYField.setText(String.valueOf(p.owner().location().y()));
    locZField.setText(String.valueOf(p.owner().location().z()));
    locNameField.setText(p.owner().location().name());
  }

  private void handleSave() {
    LocaleManager lm = LocaleManager.getInstance();

    try {
      String name = getTrimmed(nameField);
      if (name.isEmpty()) throw new ValidationException("name", "must not be empty");

      Integer coordX = parseInt(coordXField, "coordinate X");
      Float coordY = parseFloat(coordYField, "coordinate Y");
      if (coordX == null) throw new ValidationException("coordinate X", "must not be empty");
      if (coordY == null) throw new ValidationException("coordinate Y", "must not be empty");

      Float price = null;
      String priceText = getTrimmed(priceField);
      if (!priceText.isEmpty()) {
        try {
          price = Float.parseFloat(priceText);
          if (price <= 0 || !Float.isFinite(price))
            throw new ValidationException("price", "must be > 0");
        } catch (NumberFormatException e) {
          throw new ValidationException("price", "invalid number");
        }
      }

      String partNumber = getTrimmed(partNumberField);
      if (partNumber.length() < 22)
        throw new ValidationException("part number", "must be at least 22 characters");
      if (partNumber.length() > 83)
        throw new ValidationException("part number", "must not exceed 83 characters");

      UnitOfMeasure uom = uomCombo.getValue();
      if (uom == null) throw new ValidationException("unit of measure", "must be selected");

      String ownerName = getTrimmed(ownerNameField);
      if (ownerName.isEmpty()) throw new ValidationException("owner name", "must not be empty");

      Date birthday = null;
      LocalDate bd = birthdayPicker.getValue();
      if (bd != null) birthday = Date.from(bd.atStartOfDay(ZoneId.systemDefault()).toInstant());

      String passport = getTrimmed(passportField);

      Float locX = parseFloat(locXField, "location X");
      Integer locY = parseInt(locYField, "location Y");
      Long locZ = parseLong(locZField, "location Z");
      if (locX == null) throw new ValidationException("location X", "must not be empty");
      if (locY == null) throw new ValidationException("location Y", "must not be empty");
      if (locZ == null) throw new ValidationException("location Z", "must not be empty");

      String locName = getTrimmed(locNameField);
      if (locName.isEmpty()) throw new ValidationException("location name", "must not be empty");
      if (locName.length() > 986)
        throw new ValidationException("location name", "must not exceed 986 characters");

      Coordinates coordinates = new Coordinates(coordX, coordY);
      Location location = new Location(locX, locY, locZ, locName);
      Person owner = new Person(ownerName, birthday, passport, location);
      ZonedDateTime now = ZonedDateTime.now();

      if (editMode) {
        result =
            new Product(
                original.id(),
                name,
                coordinates,
                price,
                partNumber,
                uom,
                owner,
                original.creationDate(),
                original.createdBy());
      } else {
        result = new Product(null, name, coordinates, price, partNumber, uom, owner, now, null);
      }

      dialogStage.close();

    } catch (ValidationException e) {
      errorLabel.setText(lm.getString("product.error.invalidField", e.field, e.reason));
      errorLabel.setVisible(true);
    }
  }

  private String getTrimmed(TextField field) {
    String text = field.getText();
    return text != null ? text.trim() : "";
  }

  private Integer parseInt(TextField field, String name) {
    String text = getTrimmed(field);
    if (text.isEmpty()) return null;
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw new ValidationException(name, "invalid integer");
    }
  }

  private Float parseFloat(TextField field, String name) {
    String text = getTrimmed(field);
    if (text.isEmpty()) return null;
    try {
      float val = Float.parseFloat(text);
      if (!Float.isFinite(val)) throw new ValidationException(name, "must be finite");
      return val;
    } catch (NumberFormatException e) {
      throw new ValidationException(name, "invalid number");
    }
  }

  private Long parseLong(TextField field, String name) {
    String text = getTrimmed(field);
    if (text.isEmpty()) return null;
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException e) {
      throw new ValidationException(name, "invalid long");
    }
  }

  private static class ValidationException extends RuntimeException {
    final String field;
    final String reason;

    ValidationException(String field, String reason) {
      super(field + ": " + reason);
      this.field = field;
      this.reason = reason;
    }
  }
}
