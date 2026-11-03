import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.geom.Path2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

public class paint extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final int MAX_HISTORY = 10;
    private static final Locale[] LOCALES = {
        Locale.FRENCH, Locale.ENGLISH, new Locale("es")
    };
    private static final String[] LANGUAGE_NAMES = {
        "Français", "English", "Español"
    };

    private final DrawingCanvas canvas = new DrawingCanvas();
    private Locale locale = Locale.getDefault();
    private ResourceBundle messages;
    public paint() {
        locale = supportedLocale(locale);
        messages = bundleFor(locale);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(700, 500));
        setSize(1100, 760);
        setLocationRelativeTo(null);
        refreshInterface();
    }

    private static Locale supportedLocale(Locale requested) {
        String language = requested.getLanguage();
        for (Locale candidate : LOCALES) {
            if (candidate.getLanguage().equals(language)) {
                return candidate;
            }
        }
        return Locale.ENGLISH;
    }

    private static ResourceBundle bundleFor(Locale selectedLocale) {
        return ResourceBundle.getBundle(
            "PaintMessages",
            selectedLocale,
            ResourceBundle.Control.getNoFallbackControl(
                ResourceBundle.Control.FORMAT_PROPERTIES
            )
        );
    }

    private void refreshInterface() {
        setTitle(messages.getString("app.title"));
        setJMenuBar(createMenuBar());

        JPanel content = new JPanel(new BorderLayout());
        content.add(createToolbar(), BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.getViewport().setBackground(new Color(225, 228, 232));
        content.add(scrollPane, BorderLayout.CENTER);
        setContentPane(content);
        revalidate();
        repaint();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu(messages.getString("menu.file"));
        fileMenu.add(menuItem("file.open", this::openImage));
        fileMenu.add(menuItem("file.save", this::saveImage));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("file.exit", () -> dispose()));

        JMenu editMenu = new JMenu(messages.getString("menu.edit"));
        editMenu.add(menuItem("edit.undo", canvas::undo));
        editMenu.add(menuItem("edit.redo", canvas::redo));
        editMenu.addSeparator();
        editMenu.add(menuItem("edit.clear", this::clearCanvas));

        JMenu helpMenu = new JMenu(messages.getString("menu.help"));
        helpMenu.add(menuItem("help.about", () -> JOptionPane.showMessageDialog(
            this,
            messages.getString("about.message"),
            messages.getString("menu.help"),
            JOptionPane.INFORMATION_MESSAGE
        )));

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private JMenuItem menuItem(String key, Runnable action) {
        JMenuItem item = new JMenuItem(messages.getString(key));
        item.addActionListener(event -> action.run());
        return item;
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        ButtonGroup tools = new ButtonGroup();
        addToolButton(toolbar, tools, DrawingCanvas.Tool.PENCIL, "tool.pencil");
        addToolButton(toolbar, tools, DrawingCanvas.Tool.LINE, "tool.line");
        addToolButton(toolbar, tools, DrawingCanvas.Tool.RECTANGLE, "tool.rectangle");
        addToolButton(toolbar, tools, DrawingCanvas.Tool.ELLIPSE, "tool.ellipse");
        addToolButton(toolbar, tools, DrawingCanvas.Tool.ERASER, "tool.eraser");
        addToolButton(toolbar, tools, DrawingCanvas.Tool.BUCKET, "tool.bucket");
        addToolButton(toolbar, tools, DrawingCanvas.Tool.STAR, "tool.star");
        addToolButton(toolbar, tools, DrawingCanvas.Tool.HEART, "tool.heart");
        addToolButton(toolbar, tools, DrawingCanvas.Tool.ARROW, "tool.arrow");

        toolbar.addSeparator();
        JMenuItem undo = new JMenuItem(messages.getString("edit.undo"));
        undo.addActionListener(event -> canvas.undo());
        toolbar.add(undo);
        JMenuItem redo = new JMenuItem(messages.getString("edit.redo"));
        redo.addActionListener(event -> canvas.redo());
        toolbar.add(redo);

        toolbar.addSeparator();
        javax.swing.JButton colorButton = new javax.swing.JButton(
            messages.getString("control.color")
        );
        colorButton.setIcon(new ColorIcon(canvas.getDrawingColor()));
        colorButton.addActionListener(event -> {
            Color selected = javax.swing.JColorChooser.showDialog(
                this, messages.getString("dialog.chooseColor"), canvas.getDrawingColor()
            );
            if (selected != null) {
                canvas.setDrawingColor(selected);
                colorButton.setIcon(new ColorIcon(selected));
            }
        });
        toolbar.add(colorButton);

        toolbar.add(new javax.swing.JLabel(messages.getString("control.size")));
        JSlider sizeSlider = new JSlider(1, 40, canvas.getStrokeWidth());
        sizeSlider.setPreferredSize(new Dimension(100, 34));
        sizeSlider.setToolTipText(messages.getString("control.size"));
        sizeSlider.addChangeListener(event ->
            canvas.setStrokeWidth(((JSlider) event.getSource()).getValue())
        );
        toolbar.add(sizeSlider);

        JCheckBox fill = new JCheckBox(messages.getString("control.fill"), canvas.isFilled());
        fill.addActionListener(event -> canvas.setFilled(fill.isSelected()));
        toolbar.add(fill);

        toolbar.addSeparator();
        toolbar.add(new javax.swing.JLabel(messages.getString("control.language")));
        JComboBox<String> languagePicker = new JComboBox<>(LANGUAGE_NAMES);
        languagePicker.setSelectedIndex(localeIndex(locale));
        languagePicker.addActionListener(event -> {
            int selectedIndex = languagePicker.getSelectedIndex();
            if (selectedIndex >= 0) {
                locale = LOCALES[selectedIndex];
                messages = bundleFor(locale);
                refreshInterface();
            }
        });
        toolbar.add(languagePicker);
        return toolbar;
    }

    private void addToolButton(
        JToolBar toolbar, ButtonGroup group, DrawingCanvas.Tool tool, String key
    ) {
        JToggleButton button = new JToggleButton(new ToolIcon(tool));
        button.setToolTipText(messages.getString(key));
        button.getAccessibleContext().setAccessibleName(messages.getString(key));
        button.setPreferredSize(new Dimension(38, 34));
        button.setSelected(canvas.getTool() == tool);
        button.addActionListener(event -> canvas.setTool(tool));
        group.add(button);
        toolbar.add(button);
    }

    private static int localeIndex(Locale selectedLocale) {
        for (int i = 0; i < LOCALES.length; i++) {
            if (LOCALES[i].getLanguage().equals(selectedLocale.getLanguage())) {
                return i;
            }
        }
        return 1;
    }

    private void clearCanvas() {
        int answer = JOptionPane.showConfirmDialog(
            this,
            messages.getString("dialog.clear.confirm"),
            messages.getString("edit.clear"),
            JOptionPane.YES_NO_OPTION
        );
        if (answer == JOptionPane.YES_OPTION) {
            canvas.clear();
        }
    }

    private void openImage() {
        JFileChooser chooser = imageChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                showError(messages.getString("error.unsupported"));
                return;
            }
            canvas.loadImage(image);
        } catch (IOException exception) {
            showError(messages.getString("error.open") + "\n" + exception.getMessage());
        }
    }

    private void saveImage() {
        JFileChooser chooser = imageChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
            file = new File(file.getParentFile(), file.getName() + ".png");
        }
        try {
            ImageIO.write(canvas.getImage(), "png", file);
        } catch (IOException exception) {
            showError(messages.getString("error.save") + "\n" + exception.getMessage());
        }
    }

    private JFileChooser imageChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
            messages.getString("file.images"), "png", "jpg", "jpeg", "bmp", "gif"
        ));
        return chooser;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
            this, message, messages.getString("dialog.error"), JOptionPane.ERROR_MESSAGE
        );
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new paint().setVisible(true));
    }

    private static final class ColorIcon implements javax.swing.Icon {
        private final Color color;

        private ColorIcon(Color color) {
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }

        @Override
        public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            graphics.setColor(color);
            graphics.fillRect(x + 2, y + 2, 16, 16);
            graphics.setColor(Color.DARK_GRAY);
            graphics.drawRect(x + 2, y + 2, 16, 16);
        }
    }

    private static final class ToolIcon implements javax.swing.Icon {
        private final DrawingCanvas.Tool tool;

        private ToolIcon(DrawingCanvas.Tool tool) {
            this.tool = tool;
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }

        @Override
        public void paintIcon(
            java.awt.Component component, Graphics graphics, int x, int y
        ) {
            Graphics2D iconGraphics = (Graphics2D) graphics.create();
            iconGraphics.translate(x, y);
            iconGraphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON
            );
            iconGraphics.setStroke(new BasicStroke(
                1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
            ));

            if (tool == DrawingCanvas.Tool.PENCIL) {
                iconGraphics.setColor(new Color(55, 65, 81));
                iconGraphics.drawLine(4, 16, 15, 5);
                iconGraphics.drawLine(3, 17, 7, 16);
                iconGraphics.setColor(new Color(245, 183, 85));
                iconGraphics.drawLine(13, 5, 15, 7);
            } else if (tool == DrawingCanvas.Tool.LINE) {
                iconGraphics.setColor(new Color(55, 65, 81));
                iconGraphics.drawLine(3, 16, 17, 4);
            } else if (tool == DrawingCanvas.Tool.RECTANGLE) {
                iconGraphics.setColor(new Color(55, 125, 190));
                iconGraphics.drawRect(3, 4, 14, 12);
            } else if (tool == DrawingCanvas.Tool.ELLIPSE) {
                iconGraphics.setColor(new Color(55, 125, 190));
                iconGraphics.drawOval(3, 4, 14, 12);
            } else if (tool == DrawingCanvas.Tool.ERASER) {
                iconGraphics.setColor(new Color(219, 111, 143));
                iconGraphics.drawRoundRect(4, 5, 12, 10, 3, 3);
                iconGraphics.drawLine(7, 14, 12, 7);
            } else if (tool == DrawingCanvas.Tool.BUCKET) {
                iconGraphics.setColor(new Color(69, 140, 191));
                iconGraphics.drawLine(5, 6, 14, 15);
                iconGraphics.drawLine(14, 15, 17, 12);
                iconGraphics.drawLine(5, 6, 3, 9);
                iconGraphics.drawLine(3, 9, 10, 16);
                iconGraphics.drawLine(10, 16, 13, 13);
                iconGraphics.setColor(new Color(72, 158, 211));
                iconGraphics.fillOval(14, 3, 3, 3);
                iconGraphics.fillOval(16, 7, 2, 2);
            } else {
                Shape motif = DrawingCanvas.motifShape(tool, 10, 10, 15);
                if (tool == DrawingCanvas.Tool.STAR) {
                    iconGraphics.setColor(new Color(255, 195, 66));
                    iconGraphics.fill(motif);
                    iconGraphics.setColor(new Color(184, 125, 25));
                } else if (tool == DrawingCanvas.Tool.HEART) {
                    iconGraphics.setColor(new Color(238, 112, 137));
                    iconGraphics.fill(motif);
                    iconGraphics.setColor(new Color(180, 67, 93));
                } else {
                    iconGraphics.setColor(new Color(91, 157, 211));
                    iconGraphics.fill(motif);
                    iconGraphics.setColor(new Color(48, 104, 151));
                }
                iconGraphics.draw(motif);
            }
            iconGraphics.dispose();
        }
    }

    private static final class DrawingCanvas extends JPanel {
        private static final long serialVersionUID = 1L;

        private enum Tool {
            PENCIL, LINE, RECTANGLE, ELLIPSE, ERASER, BUCKET, STAR, HEART, ARROW
        }

        private BufferedImage image = new BufferedImage(
            1000, 650, BufferedImage.TYPE_INT_RGB
        );
        private final Deque<BufferedImage> undoHistory = new ArrayDeque<>();
        private final Deque<BufferedImage> redoHistory = new ArrayDeque<>();
        private Tool tool = Tool.PENCIL;
        private Color drawingColor = Color.BLACK;
        private int strokeWidth = 4;
        private boolean filled;
        private int startX;
        private int startY;
        private int previousX;
        private int previousY;
        private boolean drawing;
        private boolean changed;
        private BufferedImage beforeStroke;

        private DrawingCanvas() {
            setBackground(Color.WHITE);
            setOpaque(true);
            setBorder(BorderFactory.createLineBorder(new Color(170, 174, 180)));
            setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.dispose();

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (event.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    if (tool == Tool.BUCKET) {
                        fillAt(event.getX(), event.getY());
                        return;
                    }
                    startX = previousX = event.getX();
                    startY = previousY = event.getY();
                    beforeStroke = copyImage(image);
                    drawing = true;
                    changed = false;
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (!drawing) {
                        return;
                    }
                    if (tool == Tool.PENCIL || tool == Tool.ERASER) {
                        Graphics2D graphics = image.createGraphics();
                        configureGraphics(graphics);
                        graphics.setColor(tool == Tool.ERASER ? Color.WHITE : drawingColor);
                        graphics.drawLine(previousX, previousY, event.getX(), event.getY());
                        graphics.dispose();
                        previousX = event.getX();
                        previousY = event.getY();
                        changed = true;
                    } else {
                        changed = true;
                    }
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (!drawing) {
                        return;
                    }
                    int endX = event.getX();
                    int endY = event.getY();
                    if (isShapeTool(tool)) {
                        Graphics2D graphics = image.createGraphics();
                        configureGraphics(graphics);
                        drawShape(graphics, endX, endY);
                        graphics.dispose();
                        changed = true;
                    } else if (startX == endX && startY == endY) {
                        Graphics2D graphics = image.createGraphics();
                        configureGraphics(graphics);
                        graphics.setColor(tool == Tool.ERASER ? Color.WHITE : drawingColor);
                        graphics.fillOval(
                            startX - strokeWidth / 2, startY - strokeWidth / 2,
                            strokeWidth, strokeWidth
                        );
                        graphics.dispose();
                        changed = true;
                    }
                    if (changed) {
                        remember(undoHistory, beforeStroke);
                        redoHistory.clear();
                    }
                    drawing = false;
                    changed = false;
                    beforeStroke = null;
                    repaint();
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        private void drawShape(Graphics2D graphics, int endX, int endY) {
            graphics.setColor(drawingColor);
            int x = Math.min(startX, endX);
            int y = Math.min(startY, endY);
            int width = Math.abs(endX - startX);
            int height = Math.abs(endY - startY);
            if (tool == Tool.LINE) {
                graphics.drawLine(startX, startY, endX, endY);
            } else if (tool == Tool.RECTANGLE) {
                if (filled) {
                    graphics.fillRect(x, y, width, height);
                } else {
                    graphics.drawRect(x, y, width, height);
                }
            } else if (tool == Tool.ELLIPSE) {
                if (filled) {
                    graphics.fillOval(x, y, width, height);
                } else {
                    graphics.drawOval(x, y, width, height);
                }
            } else if (isMotifTool(tool)) {
                int diameter = Math.max(12, strokeWidth * 3);
                Shape motif = motifShape(tool, endX, endY, diameter);
                if (filled) {
                    graphics.fill(motif);
                } else {
                    graphics.draw(motif);
                }
            }
        }

        private static boolean isMotifTool(Tool tool) {
            return tool == Tool.STAR || tool == Tool.HEART || tool == Tool.ARROW;
        }

        private void fillAt(int x, int y) {
            if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
                return;
            }
            int targetColor = image.getRGB(x, y);
            int replacementColor = drawingColor.getRGB();
            if (targetColor == replacementColor) {
                return;
            }

            BufferedImage snapshot = copyImage(image);
            Deque<Integer> pending = new ArrayDeque<>();
            pending.push(y * image.getWidth() + x);
            while (!pending.isEmpty()) {
                int position = pending.pop();
                int currentX = position % image.getWidth();
                int currentY = position / image.getWidth();
                if (image.getRGB(currentX, currentY) != targetColor) {
                    continue;
                }

                int left = currentX;
                while (left > 0 && image.getRGB(left - 1, currentY) == targetColor) {
                    left--;
                }
                int right = currentX;
                while (right + 1 < image.getWidth()
                    && image.getRGB(right + 1, currentY) == targetColor) {
                    right++;
                }
                for (int fillX = left; fillX <= right; fillX++) {
                    image.setRGB(fillX, currentY, replacementColor);
                }

                for (int neighborY : new int[] {currentY - 1, currentY + 1}) {
                    if (neighborY < 0 || neighborY >= image.getHeight()) {
                        continue;
                    }
                    int scanX = left;
                    while (scanX <= right) {
                        if (image.getRGB(scanX, neighborY) == targetColor) {
                            pending.push(neighborY * image.getWidth() + scanX);
                            while (scanX <= right
                                && image.getRGB(scanX, neighborY) == targetColor) {
                                scanX++;
                            }
                        } else {
                            scanX++;
                        }
                    }
                }
            }
            remember(undoHistory, snapshot);
            redoHistory.clear();
            repaint();
        }

        private static boolean isShapeTool(Tool tool) {
            return tool == Tool.LINE || tool == Tool.RECTANGLE || tool == Tool.ELLIPSE
                || isMotifTool(tool);
        }

        private static Shape motifShape(Tool tool, double centerX, double centerY, double size) {
            double half = size / 2.0;
            Path2D.Double path = new Path2D.Double();
            if (tool == Tool.STAR) {
                for (int i = 0; i < 10; i++) {
                    double angle = -Math.PI / 2 + i * Math.PI / 5;
                    double radius = i % 2 == 0 ? half : half * 0.43;
                    double x = centerX + Math.cos(angle) * radius;
                    double y = centerY + Math.sin(angle) * radius;
                    if (i == 0) {
                        path.moveTo(x, y);
                    } else {
                        path.lineTo(x, y);
                    }
                }
                path.closePath();
            } else if (tool == Tool.HEART) {
                path.moveTo(centerX, centerY + half * 0.85);
                path.curveTo(
                    centerX - half * 0.25, centerY + half * 0.58,
                    centerX - half, centerY + half * 0.12,
                    centerX - half, centerY - half * 0.28
                );
                path.curveTo(
                    centerX - half, centerY - half * 0.88,
                    centerX - half * 0.2, centerY - half,
                    centerX, centerY - half * 0.48
                );
                path.curveTo(
                    centerX + half * 0.2, centerY - half,
                    centerX + half, centerY - half * 0.88,
                    centerX + half, centerY - half * 0.28
                );
                path.curveTo(
                    centerX + half, centerY + half * 0.12,
                    centerX + half * 0.25, centerY + half * 0.58,
                    centerX, centerY + half * 0.85
                );
                path.closePath();
            } else {
                path.moveTo(centerX - half, centerY - half * 0.3);
                path.lineTo(centerX + half * 0.15, centerY - half * 0.3);
                path.lineTo(centerX + half * 0.15, centerY - half * 0.68);
                path.lineTo(centerX + half, centerY);
                path.lineTo(centerX + half * 0.15, centerY + half * 0.68);
                path.lineTo(centerX + half * 0.15, centerY + half * 0.3);
                path.lineTo(centerX - half, centerY + half * 0.3);
                path.closePath();
            }
            return path;
        }

        private void configureGraphics(Graphics2D graphics) {
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON
            );
            graphics.setStroke(new BasicStroke(
                strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
            ));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D canvasGraphics = (Graphics2D) graphics.create();
            if (drawing && beforeStroke != null
                && isShapeTool(tool)) {
                canvasGraphics.drawImage(beforeStroke, 0, 0, null);
                configureGraphics(canvasGraphics);
                canvasGraphics.setColor(drawingColor);
                drawShape(canvasGraphics, getMousePositionX(), getMousePositionY());
            } else {
                canvasGraphics.drawImage(image, 0, 0, null);
            }
            canvasGraphics.dispose();
        }

        private int getMousePositionX() {
            java.awt.Point point = getMousePosition();
            return point == null ? startX : point.x;
        }

        private int getMousePositionY() {
            java.awt.Point point = getMousePosition();
            return point == null ? startY : point.y;
        }

        private Tool getTool() {
            return tool;
        }

        private void setTool(Tool tool) {
            this.tool = tool;
        }

        private Color getDrawingColor() {
            return drawingColor;
        }

        private void setDrawingColor(Color drawingColor) {
            this.drawingColor = drawingColor;
        }

        private int getStrokeWidth() {
            return strokeWidth;
        }

        private void setStrokeWidth(int strokeWidth) {
            this.strokeWidth = strokeWidth;
        }

        private boolean isFilled() {
            return filled;
        }

        private void setFilled(boolean filled) {
            this.filled = filled;
        }

        private BufferedImage getImage() {
            return copyImage(image);
        }

        private void loadImage(BufferedImage loadedImage) {
            remember(undoHistory, copyImage(image));
            redoHistory.clear();
            image = new BufferedImage(
                loadedImage.getWidth(), loadedImage.getHeight(), BufferedImage.TYPE_INT_RGB
            );
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(loadedImage, 0, 0, null);
            graphics.dispose();
            setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
            revalidate();
            repaint();
        }

        private void clear() {
            remember(undoHistory, copyImage(image));
            redoHistory.clear();
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.dispose();
            repaint();
        }

        private void undo() {
            if (undoHistory.isEmpty()) {
                return;
            }
            remember(redoHistory, copyImage(image));
            image = undoHistory.removeLast();
            updateCanvasSize();
            repaint();
        }

        private void redo() {
            if (redoHistory.isEmpty()) {
                return;
            }
            remember(undoHistory, copyImage(image));
            image = redoHistory.removeLast();
            updateCanvasSize();
            repaint();
        }

        private void updateCanvasSize() {
            setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
            revalidate();
        }

        private static void remember(Deque<BufferedImage> history, BufferedImage snapshot) {
            if (history.size() == MAX_HISTORY) {
                history.removeFirst();
            }
            history.addLast(snapshot);
        }

        private static BufferedImage copyImage(BufferedImage source) {
            BufferedImage copy = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB
            );
            Graphics2D graphics = copy.createGraphics();
            graphics.drawImage(source, 0, 0, null);
            graphics.dispose();
            return copy;
        }

    }
}