package ru.app.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import ru.app.object.Product;

public class CanvasRenderer {

  private static final float BASE_SIZE = 30;
  private static final float PRICE_SCALE = 0.5f;
  private static final double ANIMATION_DURATION_MS = 3000;
  private static final double PADDING = 50;

  private final Canvas canvas;
  private final Map<Integer, ObjectBounds> boundsMap = new HashMap<>();
  private final Map<Integer, Double> animationProgress = new HashMap<>();
  private final Map<Integer, Product> knownProducts = new HashMap<>();
  private final Map<Integer, Product> removingProducts = new HashMap<>();
  private final Map<Integer, Integer> productHashes = new HashMap<>();

  private Product selectedProduct;
  private Runnable onEditRequest;
  private Consumer<List<Product>> onMultipleProductsClick;
  private Consumer<Product> onTableSelect;
  private Product lastClickedProduct;
  private List<Product> currentProducts;
  private boolean animating;
  private AnimationTimer animationTimer;
  private long animationStartTime;
  private List<Integer> animatingIds;

  public CanvasRenderer(Canvas canvas) {
    this.canvas = canvas;
    setupClickHandler();
  }

  private static Color colorForUser(String username) {
    if (username == null) return Color.GRAY;
    int hash = Math.abs(username.hashCode());
    double hue = (hash * 137.508) % 360;
    double saturation = 0.65 + (hash % 3) * 0.1;
    double brightness = 0.55 + (hash % 2) * 0.15;
    return Color.hsb(hue, Math.min(saturation, 0.95), Math.min(brightness, 0.85));
  }

  private static String formatCoord(double v) {
    if (v == (long) v) return String.valueOf((long) v);
    return String.format("%.1f", v);
  }

  private static double niceStep(double range, int targetTicks) {
    double rough = range / targetTicks;
    double magnitude = Math.pow(10, Math.floor(Math.log10(rough)));
    double normalized = rough / magnitude;
    double nice;
    if (normalized <= 1.5) nice = 1;
    else if (normalized <= 3.5) nice = 2;
    else if (normalized <= 7.5) nice = 5;
    else nice = 10;
    return nice * magnitude;
  }

  public void setSelectedProduct(Product p) {
    this.selectedProduct = p;
    if (currentProducts != null) render(currentProducts);
  }

  public void setOnEditRequest(Runnable r) {
    this.onEditRequest = r;
  }

  public void setOnMultipleProductsClick(Consumer<List<Product>> c) {
    this.onMultipleProductsClick = c;
  }

  public void setOnTableSelect(Consumer<Product> c) {
    this.onTableSelect = c;
  }

  public Product getLastClickedProduct() {
    return lastClickedProduct;
  }

  public void render(List<Product> products) {
    this.currentProducts = products;

    Set<Integer> newIds = new HashSet<>();
    for (Product p : products) newIds.add(p.id());

    // Clean up fully-animated removals
    removingProducts.keySet().removeIf(id -> animationProgress.getOrDefault(id, 1.0) <= 0.0);

    // Detect new removals: products in knownProducts but not in new list
    for (Integer id : new ArrayList<>(knownProducts.keySet())) {
      if (!newIds.contains(id) && !removingProducts.containsKey(id)) {
        removingProducts.put(id, knownProducts.get(id));
        animationProgress.put(id, 1.0);
      }
    }

    // Cancel removal if a formerly-removed product reappears
    for (Integer id : new ArrayList<>(removingProducts.keySet())) {
      if (newIds.contains(id)) {
        removingProducts.remove(id);
        animationProgress.put(id, 1.0);
      }
    }

    // Retain current products
    knownProducts.keySet().retainAll(newIds);
    productHashes.keySet().retainAll(newIds);

    // Detect new or changed products via hash
    for (Product p : products) {
      int hash = Objects.hash(p.coordinates().x(), p.coordinates().y(), p.price());
      Integer oldHash = productHashes.get(p.id());

      if (!knownProducts.containsKey(p.id())) {
        animationProgress.put(p.id(), 0.0);
      } else if (oldHash != null && oldHash != hash) {
        animationProgress.put(p.id(), 0.0);
      }

      productHashes.put(p.id(), hash);
      knownProducts.put(p.id(), p);
    }

    draw();

    if (!animating) {
      boolean pending = false;
      for (Map.Entry<Integer, Double> e : animationProgress.entrySet()) {
        double val = e.getValue();
        if (removingProducts.containsKey(e.getKey())) {
          if (val > 0.0) { pending = true; break; }
        } else {
          if (val < 1.0) { pending = true; break; }
        }
      }
      if (pending) startAnimation();
    }
  }

  private void draw() {
    GraphicsContext gc = canvas.getGraphicsContext2D();
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    gc.clearRect(0, 0, w, h);

    if ((currentProducts == null || currentProducts.isEmpty()) && removingProducts.isEmpty()) {
      knownProducts.clear();
      productHashes.clear();
      animationProgress.clear();
      if (animationTimer != null) animationTimer.stop();
      animating = false;
      return;
    }

    // Build combined list of current + removing products
    List<Product> allProducts = new ArrayList<>();
    if (currentProducts != null) allProducts.addAll(currentProducts);
    allProducts.addAll(removingProducts.values());

    if (allProducts.isEmpty()) return;

    double minX = allProducts.stream().mapToDouble(p -> (double) p.coordinates().x()).min().orElse(0);
    double maxX = allProducts.stream().mapToDouble(p -> (double) p.coordinates().x()).max().orElse(1);
    double minY = allProducts.stream().mapToDouble(p -> (double) p.coordinates().y()).min().orElse(0);
    double maxY = allProducts.stream().mapToDouble(p -> (double) p.coordinates().y()).max().orElse(1);

    double rangeX = maxX - minX;
    double rangeY = maxY - minY;
    if (rangeX < 1) rangeX = 1;
    if (rangeY < 1) rangeY = 1;

    double drawW = Math.max(0, w - 2 * PADDING);
    double drawH = Math.max(0, h - 2 * PADDING);

    double maxDrawSize = BASE_SIZE;
    for (Product p : allProducts) {
      double s = BASE_SIZE;
      if (p.price() != null && p.price() > 0) s += p.price() * PRICE_SCALE;
      if (drawW > 0 && s > drawW / 4) s = drawW / 4;
      if (drawH > 0 && s > drawH / 4) s = drawH / 4;
      if (s > maxDrawSize) maxDrawSize = s;
    }

    double margin = maxDrawSize / 2;
    double effectivePadding = PADDING + margin;
    double eDrawW = Math.max(0, w - 2 * effectivePadding);
    double eDrawH = Math.max(0, h - 2 * effectivePadding);

    drawAxes(gc, effectivePadding, eDrawW, eDrawH, minX, maxX, minY, maxY);

    boundsMap.clear();

    List<Product> sorted = new ArrayList<>(allProducts);
    sorted.sort((a, b) -> {
      double sizeA = BASE_SIZE;
      if (a.price() != null && a.price() > 0) sizeA += a.price() * PRICE_SCALE;
      double sizeB = BASE_SIZE;
      if (b.price() != null && b.price() > 0) sizeB += b.price() * PRICE_SCALE;
      return Double.compare(sizeB, sizeA);
    });

    for (Product p : sorted) {
      double nx = (p.coordinates().x() - minX) / rangeX;
      double ny = 1.0 - (p.coordinates().y() - minY) / rangeY;
      double cx = eDrawW > 0 ? effectivePadding + nx * eDrawW : effectivePadding;
      double cy = eDrawH > 0 ? effectivePadding + ny * eDrawH : effectivePadding;

      double size = BASE_SIZE;
      if (p.price() != null && p.price() > 0) size += p.price() * PRICE_SCALE;
      if (drawW > 0 && size > drawW / 4) size = drawW / 4;
      if (drawH > 0 && size > drawH / 4) size = drawH / 4;

      double progress = animationProgress.getOrDefault(p.id(), 1.0);
      if (progress < 0.01) continue;

      double drawSize = size * progress;
      double x = cx - drawSize / 2;
      double y = cy - drawSize / 2;

      boolean isRemoving = removingProducts.containsKey(p.id());
      Color color = colorForUser(p.createdBy());
      gc.setGlobalAlpha(isRemoving ? 0.75 * progress : 0.75);
      gc.setFill(color);
      gc.fillRoundRect(x, y, drawSize, drawSize, 8, 8);
      gc.setGlobalAlpha(1.0);
      gc.setStroke(color.darker());
      gc.setLineWidth(1.5);
      gc.strokeRoundRect(x, y, drawSize, drawSize, 8, 8);

      if (!isRemoving && selectedProduct != null && Objects.equals(p.id(), selectedProduct.id())) {
        gc.setGlobalAlpha(1.0);
        gc.setStroke(Color.GOLD);
        gc.setLineWidth(3);
        gc.strokeRoundRect(x - 2, y - 2, drawSize + 4, drawSize + 4, 10, 10);
      }

      if (drawSize > 20 && !isRemoving) {
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 10));
        gc.fillText(String.valueOf(p.id()), x + 3, y + 12);
      }

      if (!isRemoving) {
        boundsMap.put(p.id(), new ObjectBounds(x, y, drawSize, drawSize, p));
      }
    }
  }

  private void drawAxes(GraphicsContext gc, double pad, double drawW, double drawH,
                        double minX, double maxX, double minY, double maxY) {
    if (drawW <= 0 || drawH <= 0) return;

    double left = pad;
    double right = pad + drawW;
    double top = pad;
    double bottom = pad + drawH;
    double tickSize = 6;

    gc.setStroke(Color.rgb(48, 54, 61));
    gc.setLineWidth(1);

    gc.strokeLine(left, bottom, right, bottom);
    gc.strokeLine(left, top, left, bottom);

    gc.setFill(Color.rgb(145, 152, 161));
    gc.setFont(Font.font("Monospace", FontWeight.NORMAL, 10));

    drawGridWithDivisions(gc, left, top, right, bottom, drawW, drawH, minX, maxX, minY, maxY, tickSize);

    gc.setFill(Color.rgb(145, 152, 161, 0.5));
    gc.fillText("X", right + 4, bottom - 2);
    gc.fillText("Y", left + 4, top - 4);
  }

  private void drawGridWithDivisions(GraphicsContext gc, double left, double top,
                                     double right, double bottom,
                                     double drawW, double drawH,
                                     double minX, double maxX, double minY, double maxY,
                                     double tickSize) {
    double rangeX = maxX - minX;
    double rangeY = maxY - minY;
    if (rangeX <= 0 || rangeY <= 0) return;

    double stepX = niceStep(rangeX, 6);
    double stepY = niceStep(rangeY, 6);

    double startX = Math.ceil(minX / stepX) * stepX;
    double startY = Math.ceil(minY / stepY) * stepY;

    gc.setLineDashes(3, 4);
    gc.setStroke(Color.rgb(145, 152, 161, 0.35));
    gc.setLineWidth(1);
    gc.setFill(Color.rgb(145, 152, 161));
    gc.setFont(Font.font("Monospace", FontWeight.NORMAL, 10));

    for (double v = startX; v <= maxX; v += stepX) {
      double nx = (v - minX) / rangeX;
      double px = left + nx * drawW;
      gc.strokeLine(px, top, px, bottom);
      gc.setLineDashes(null);
      gc.strokeLine(px, bottom + tickSize, px, bottom - tickSize);
      gc.setLineDashes(3, 4);
      String label = formatCoord(v);
      double tw = gc.getFont().getSize() * label.length() * 0.6;
      gc.fillText(label, px - tw / 2, bottom + 14);
    }

    for (double v = startY; v <= maxY; v += stepY) {
      double ny = 1.0 - (v - minY) / rangeY;
      double py = top + ny * drawH;
      gc.strokeLine(left, py, right, py);
      gc.setLineDashes(null);
      gc.strokeLine(left - tickSize, py, left + tickSize, py);
      gc.setLineDashes(3, 4);
      String label = formatCoord(v);
      double tw = gc.getFont().getSize() * label.length() * 0.6;
      gc.fillText(label, left - tw - 6, py + 4);
    }

    gc.setLineDashes(null);
  }

  private void startAnimation() {
    if (animating) return;

    animatingIds = new ArrayList<>();
    for (Map.Entry<Integer, Double> e : animationProgress.entrySet()) {
      Integer id = e.getKey();
      double val = e.getValue();
      if (removingProducts.containsKey(id)) {
        if (val > 0.0) animatingIds.add(id);
      } else {
        if (val < 1.0) animatingIds.add(id);
      }
    }
    if (animatingIds.isEmpty()) return;

    animating = true;
    animationStartTime = System.nanoTime();

    if (animationTimer == null) {
      animationTimer =
          new AnimationTimer() {
            @Override
            public void handle(long now) {
              if (!animating) return;

              double elapsed = (now - animationStartTime) / 1_000_000_000.0;
              double durationSec = ANIMATION_DURATION_MS / 1000.0;
              double t = Math.min(elapsed / durationSec, 1.0);
              double eased = 1 - Math.pow(1 - t, 3);

              for (Integer id : animatingIds) {
                if (!animationProgress.containsKey(id)) continue;
                if (removingProducts.containsKey(id)) {
                  animationProgress.put(id, Math.max(1.0 - eased, 0.0));
                } else {
                  animationProgress.put(id, Math.min(eased, 1.0));
                }
              }

              draw();

              if (t >= 1.0) {
                for (Integer id : animatingIds) {
                  if (removingProducts.containsKey(id)) {
                    animationProgress.put(id, 0.0);
                  } else {
                    animationProgress.put(id, 1.0);
                  }
                }
                animating = false;
                animationTimer.stop();

                boolean pending = false;
                for (Map.Entry<Integer, Double> e : animationProgress.entrySet()) {
                  double val = e.getValue();
                  if (removingProducts.containsKey(e.getKey())) {
                    if (val > 0.0) { pending = true; break; }
                  } else {
                    if (val < 1.0) { pending = true; break; }
                  }
                }
                if (pending) {
                  startAnimation();
                }
              }
            }
          };
    }

    animationTimer.start();
  }

  private void setupClickHandler() {
    canvas.setOnMouseClicked(
        new javafx.event.EventHandler<MouseEvent>() {
          private static final long DOUBLE_CLICK_MS = 300;
          private long lastClickTime;
          private Product lastSingleClickProduct;

          @Override
          public void handle(MouseEvent e) {
            if (e.getButton() != MouseButton.PRIMARY) return;
            double mx = e.getX();
            double my = e.getY();

            List<Product> hits = new ArrayList<>();
            for (ObjectBounds ob : boundsMap.values()) {
              if (mx >= ob.x && mx <= ob.x + ob.size && my >= ob.y && my <= ob.y + ob.size) {
                hits.add(ob.product);
              }
            }

            if (hits.isEmpty()) {
              if (onTableSelect != null) onTableSelect.accept(null);
              lastClickTime = 0;
              lastSingleClickProduct = null;
              return;
            }

            if (hits.size() == 1) {
              long now = System.currentTimeMillis();
              Product clicked = hits.get(0);

              if (now - lastClickTime < DOUBLE_CLICK_MS && clicked == lastSingleClickProduct) {
                lastClickedProduct = clicked;
                if (onEditRequest != null) onEditRequest.run();
                lastClickTime = 0;
                lastSingleClickProduct = null;
              } else {
                lastClickedProduct = clicked;
                lastSingleClickProduct = clicked;
                lastClickTime = now;
                if (onTableSelect != null) onTableSelect.accept(clicked);
              }
            } else {
              if (onMultipleProductsClick != null) {
                onMultipleProductsClick.accept(hits);
              }
            }
          }
        });
  }

  private record ObjectBounds(double x, double y, double size, double _pad, Product product) {}
}
