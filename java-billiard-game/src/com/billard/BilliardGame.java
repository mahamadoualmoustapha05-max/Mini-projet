package com.billard;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class BilliardGame extends JPanel implements ActionListener {
    private static final int TABLE_WIDTH = 800;
    private static final int TABLE_HEIGHT = 400;
    private static final int BORDER = 40;
    private static final int POCKET_RADIUS = 22;

    private final GameMode currentMode;
    private final List<Ball> balls = new ArrayList<>();
    private final List<Point> pockets = new ArrayList<>();
    private final Random random = new Random();
    private final Timer gameLoop;
    private Ball cueBall;
    private int currentPlayer = 1;
    private int scoreP1;
    private int scoreP2;
    private boolean ballPocketedThisTurn;
    private boolean isAiming;
    private boolean aiThinking;
    private Point mousePos = new Point(0, 0);

    private BilliardGame(GameMode mode) {
        currentMode = mode;
        setPreferredSize(new Dimension(
                TABLE_WIDTH + BORDER * 2,
                TABLE_HEIGHT + BORDER * 2 + 50
        ));
        setBackground(new Color(30, 30, 30));
        setFocusable(true);

        initPockets();
        resetTable();
        setupInputs();

        gameLoop = new Timer(16, this);
        gameLoop.start();
    }

    private void initPockets() {
        int left = BORDER;
        int right = BORDER + TABLE_WIDTH;
        int top = BORDER;
        int bottom = BORDER + TABLE_HEIGHT;
        int midX = BORDER + TABLE_WIDTH / 2;

        pockets.add(new Point(left, top));
        pockets.add(new Point(midX, top - 5));
        pockets.add(new Point(right, top));
        pockets.add(new Point(left, bottom));
        pockets.add(new Point(midX, bottom + 5));
        pockets.add(new Point(right, bottom));
    }

    private void resetTable() {
        balls.clear();
        cueBall = new Ball(BORDER + 200, BORDER + TABLE_HEIGHT / 2.0, 0, Color.WHITE);
        balls.add(cueBall);

        Color[] colors = {
                Color.YELLOW, Color.BLUE, Color.RED, new Color(128, 0, 128), Color.ORANGE,
                new Color(0, 100, 0), new Color(128, 0, 0), Color.BLACK,
                Color.YELLOW, Color.BLUE, Color.RED, new Color(128, 0, 128), Color.ORANGE,
                new Color(0, 100, 0), new Color(128, 0, 0)
        };

        double startX = BORDER + 550;
        double startY = BORDER + TABLE_HEIGHT / 2.0;
        double radius = Ball.RADIUS;
        int index = 0;

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col <= row; col++) {
                double x = startX + row * (radius * 1.75);
                double y = startY - (row * radius) + (col * radius * 2);
                balls.add(new Ball(x, y, index + 1, colors[index]));
                index++;
            }
        }
        currentPlayer = 1;
        scoreP1 = 0;
        scoreP2 = 0;
        ballPocketedThisTurn = false;
        aiThinking = false;
    }

    private void setupInputs() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (isMoving() || aiThinking || isAITurn()) {
                    return;
                }

                if (currentMode == GameMode.TRAINING
                        && SwingUtilities.isRightMouseButton(event)) {
                    cueBall.x = Math.max(
                            BORDER + cueBall.radius,
                            Math.min(BORDER + TABLE_WIDTH - cueBall.radius, event.getX())
                    );
                    cueBall.y = Math.max(
                            BORDER + cueBall.radius,
                            Math.min(BORDER + TABLE_HEIGHT - cueBall.radius, event.getY())
                    );
                    cueBall.vx = 0;
                    cueBall.vy = 0;
                    repaint();
                    return;
                }

                if (Math.hypot(event.getX() - cueBall.x, event.getY() - cueBall.y)
                        <= cueBall.radius * 2.5) {
                    isAiming = true;
                    mousePos = event.getPoint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (isAiming) {
                    mousePos = event.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (isAiming) {
                    isAiming = false;
                    double dx = cueBall.x - event.getX();
                    double dy = cueBall.y - event.getY();
                    double distance = Math.hypot(dx, dy);

                    if (distance > 5) {
                        double power = Math.min(distance * 0.12, 22.0);
                        double angle = Math.atan2(dy, dx);
                        cueBall.vx = Math.cos(angle) * power;
                        cueBall.vy = Math.sin(angle) * power;
                        ballPocketedThisTurn = false;
                    }
                }
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_R) {
                    resetTable();
                } else if (event.getKeyCode() == KeyEvent.VK_M) {
                    gameLoop.stop();
                    SwingUtilities.getWindowAncestor(BilliardGame.this).dispose();
                    launchGame();
                }
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        boolean wasMoving = isMoving();

        for (Ball ball : balls) {
            if (!ball.inPocket) {
                ball.update(BORDER, BORDER, TABLE_WIDTH, TABLE_HEIGHT);
                checkPockets(ball);
            }
        }

        for (int i = 0; i < balls.size(); i++) {
            for (int j = i + 1; j < balls.size(); j++) {
                Ball first = balls.get(i);
                Ball second = balls.get(j);
                if (!first.inPocket && !second.inPocket) {
                    resolveCollision(first, second);
                }
            }
        }

        if (wasMoving && !isMoving()) {
            onTurnEnd();
        }

        if (!isMoving() && isAITurn() && !aiThinking) {
            triggerAITurn();
        }

        repaint();
    }

    private void checkPockets(Ball ball) {
        for (Point pocket : pockets) {
            if (Math.hypot(ball.x - pocket.x, ball.y - pocket.y) < POCKET_RADIUS) {
                if (ball == cueBall) {
                    ball.vx = 0;
                    ball.vy = 0;
                    ball.x = BORDER + 200;
                    ball.y = BORDER + TABLE_HEIGHT / 2.0;
                    ballPocketedThisTurn = false;
                } else {
                    ball.inPocket = true;
                    ball.vx = 0;
                    ball.vy = 0;
                    ballPocketedThisTurn = true;
                    if (currentPlayer == 1) {
                        scoreP1++;
                    } else {
                        scoreP2++;
                    }
                }
                break;
            }
        }
    }

    private void onTurnEnd() {
        long remaining = balls.stream()
                .filter(ball -> !ball.inPocket && ball != cueBall)
                .count();
        if (remaining == 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "Partie terminée ! Score : J1 (" + scoreP1 + ") - J2/IA (" + scoreP2 + ")"
            );
            resetTable();
            return;
        }

        if (currentMode == GameMode.TRAINING) {
            return;
        }

        if (!ballPocketedThisTurn) {
            currentPlayer = currentPlayer == 1 ? 2 : 1;
        }
    }

    private boolean isAITurn() {
        return currentPlayer == 2
                && (currentMode == GameMode.PVE_EASY
                || currentMode == GameMode.PVE_MEDIUM
                || currentMode == GameMode.PVE_HARD);
    }

    private void triggerAITurn() {
        aiThinking = true;
        Timer aiDelay = new Timer(1000, event -> {
            BilliardAI.shoot(currentMode, cueBall, balls, pockets, random);
            aiThinking = false;
            ballPocketedThisTurn = false;
        });
        aiDelay.setRepeats(false);
        aiDelay.start();
    }

    private boolean isMoving() {
        for (Ball ball : balls) {
            if (!ball.inPocket && (Math.abs(ball.vx) > 0.01 || Math.abs(ball.vy) > 0.01)) {
                return true;
            }
        }
        return false;
    }

    private void resolveCollision(Ball first, Ball second) {
        double dx = second.x - first.x;
        double dy = second.y - first.y;
        double distance = Math.hypot(dx, dy);

        if (distance < first.radius + second.radius && distance > 0) {
            double overlap = (first.radius + second.radius - distance) / 2.0;
            double nx = dx / distance;
            double ny = dy / distance;

            first.x -= overlap * nx;
            first.y -= overlap * ny;
            second.x += overlap * nx;
            second.y += overlap * ny;

            double kx = first.vx - second.vx;
            double ky = first.vy - second.vy;
            double projection = nx * kx + ny * ky;

            first.vx -= projection * nx;
            first.vy -= projection * ny;
            second.vx += projection * nx;
            second.vy += projection * ny;
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(101, 67, 33));
        g2.fillRoundRect(BORDER - 20, BORDER - 20, TABLE_WIDTH + 40, TABLE_HEIGHT + 40, 25, 25);

        g2.setColor(new Color(25, 115, 65));
        g2.fillRect(BORDER, BORDER, TABLE_WIDTH, TABLE_HEIGHT);

        g2.setColor(Color.BLACK);
        for (Point pocket : pockets) {
            g2.fillOval(
                    pocket.x - POCKET_RADIUS,
                    pocket.y - POCKET_RADIUS,
                    POCKET_RADIUS * 2,
                    POCKET_RADIUS * 2
            );
        }

        for (Ball ball : balls) {
            if (!ball.inPocket) {
                g2.setColor(ball.color);
                g2.fillOval(
                        (int) (ball.x - ball.radius),
                        (int) (ball.y - ball.radius),
                        (int) (ball.radius * 2),
                        (int) (ball.radius * 2)
                );
                g2.setColor(Color.BLACK);
                g2.drawOval(
                        (int) (ball.x - ball.radius),
                        (int) (ball.y - ball.radius),
                        (int) (ball.radius * 2),
                        (int) (ball.radius * 2)
                );

                if (ball.number > 0) {
                    g2.setColor(ball.number == 8 ? Color.WHITE : Color.BLACK);
                    g2.setFont(new Font("Arial", Font.BOLD, 10));
                    String number = String.valueOf(ball.number);
                    g2.drawString(number, (int) ball.x - number.length() * 3, (int) ball.y + 4);
                }
            }
        }

        if (isAiming && !isMoving()) {
            double dx = cueBall.x - mousePos.x;
            double dy = cueBall.y - mousePos.y;
            double angle = Math.atan2(dy, dx);
            double distance = Math.min(Math.hypot(dx, dy), 180);

            g2.setColor(new Color(255, 255, 255, 120));
            g2.setStroke(new BasicStroke(
                    2,
                    BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_BEVEL,
                    0,
                    new float[]{6},
                    0
            ));
            g2.drawLine(
                    (int) cueBall.x,
                    (int) cueBall.y,
                    (int) (cueBall.x + Math.cos(angle) * 250),
                    (int) (cueBall.y + Math.sin(angle) * 250)
            );

            g2.setStroke(new BasicStroke(6));
            g2.setColor(new Color(210, 160, 90));
            int stickStart = (int) (cueBall.radius + 10 + distance * 0.3);
            int stickEnd = stickStart + 180;
            g2.drawLine(
                    (int) (cueBall.x - Math.cos(angle) * stickStart),
                    (int) (cueBall.y - Math.sin(angle) * stickStart),
                    (int) (cueBall.x - Math.cos(angle) * stickEnd),
                    (int) (cueBall.y - Math.sin(angle) * stickEnd)
            );
        }

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        int infoY = BORDER + TABLE_HEIGHT + 35;
        String statusText;
        if (currentMode == GameMode.TRAINING) {
            statusText = "Mode Entraînement | Boules rentrées : " + scoreP1
                    + " | [Clic droit] Placer blanche | [R] Reset | [M] Menu";
        } else {
            String playerTwo = currentMode == GameMode.PVP
                    ? "Joueur 2"
                    : "IA (" + currentMode.name().replace("PVE_", "") + ")";
            String turnName = currentPlayer == 1 ? "Joueur 1" : playerTwo;
            if (aiThinking) {
                turnName += " réfléchit...";
            }
            statusText = "Tour : " + turnName + "   |   Score : J1 [" + scoreP1
                    + "] - " + playerTwo + " [" + scoreP2 + "]   |   [M] Menu";
        }
        g2.drawString(statusText, BORDER, infoY);
    }

    public static void launchGame() {
        String[] options = {"Entraînement (Seul)", "2 Joueurs", "Contre l'IA"};
        int choice = JOptionPane.showOptionDialog(
                null,
                "Choisissez un mode de jeu :",
                "Jeu de Billard Java",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == -1) {
            System.exit(0);
        }

        GameMode selectedMode = GameMode.TRAINING;
        if (choice == 1) {
            selectedMode = GameMode.PVP;
        } else if (choice == 2) {
            String[] levels = {"Facile", "Moyen", "Difficile"};
            int aiChoice = JOptionPane.showOptionDialog(
                    null,
                    "Choisissez le niveau de l'IA :",
                    "Difficulté IA",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    levels,
                    levels[1]
            );
            if (aiChoice == -1) {
                System.exit(0);
            }
            if (aiChoice == 0) {
                selectedMode = GameMode.PVE_EASY;
            } else if (aiChoice == 1) {
                selectedMode = GameMode.PVE_MEDIUM;
            } else {
                selectedMode = GameMode.PVE_HARD;
            }
        }

        JFrame frame = new JFrame("Billard Java");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(new BilliardGame(selectedMode));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(BilliardGame::launchGame);
    }

    private enum GameMode {
        TRAINING,
        PVP,
        PVE_EASY,
        PVE_MEDIUM,
        PVE_HARD
    }

    private static final class Ball {
        private static final double RADIUS = 12.0;

        private double x;
        private double y;
        private double vx;
        private double vy;
        private final double radius = RADIUS;
        private final int number;
        private final Color color;
        private boolean inPocket;

        private Ball(double x, double y, int number, Color color) {
            this.x = x;
            this.y = y;
            this.number = number;
            this.color = color;
        }

        private void update(int minX, int minY, int width, int height) {
            x += vx;
            y += vy;

            vx *= 0.985;
            vy *= 0.985;

            if (Math.hypot(vx, vy) < 0.08) {
                vx = 0;
                vy = 0;
            }

            if (x - radius < minX) {
                x = minX + radius;
                vx = -vx * 0.9;
            }
            if (x + radius > minX + width) {
                x = minX + width - radius;
                vx = -vx * 0.9;
            }
            if (y - radius < minY) {
                y = minY + radius;
                vy = -vy * 0.9;
            }
            if (y + radius > minY + height) {
                y = minY + height - radius;
                vy = -vy * 0.9;
            }
        }
    }

    private static final class BilliardAI {
        private BilliardAI() {
        }

        private static void shoot(
                GameMode mode,
                Ball cueBall,
                List<Ball> balls,
                List<Point> pockets,
                Random random
        ) {
            List<Ball> targets = new ArrayList<>();
            for (Ball ball : balls) {
                if (!ball.inPocket && ball != cueBall) {
                    targets.add(ball);
                }
            }
            if (targets.isEmpty()) {
                return;
            }

            double angle = 0;
            double power = 14.0;

            if (mode == GameMode.PVE_EASY) {
                Ball target = targets.get(random.nextInt(targets.size()));
                angle = Math.atan2(target.y - cueBall.y, target.x - cueBall.x);
                angle += (random.nextDouble() - 0.5) * 0.5;
                power = 10 + random.nextDouble() * 8;
            } else if (mode == GameMode.PVE_MEDIUM) {
                Ball closest = targets.get(0);
                double minDist = Double.MAX_VALUE;
                for (Ball ball : targets) {
                    double distance = Math.hypot(ball.x - cueBall.x, ball.y - cueBall.y);
                    if (distance < minDist) {
                        minDist = distance;
                        closest = ball;
                    }
                }
                angle = Math.atan2(closest.y - cueBall.y, closest.x - cueBall.x);
                angle += (random.nextDouble() - 0.5) * 0.12;
                power = 15.0;
            } else if (mode == GameMode.PVE_HARD) {
                Ball bestTarget = targets.get(0);
                Point bestPocket = pockets.get(0);
                double bestScore = Double.MAX_VALUE;

                for (Ball ball : targets) {
                    for (Point pocket : pockets) {
                        double distanceToPocket = Math.hypot(
                                pocket.x - ball.x,
                                pocket.y - ball.y
                        );
                        double distanceFromCue = Math.hypot(
                                ball.x - cueBall.x,
                                ball.y - cueBall.y
                        );
                        if (distanceToPocket + distanceFromCue < bestScore) {
                            bestScore = distanceToPocket + distanceFromCue;
                            bestTarget = ball;
                            bestPocket = pocket;
                        }
                    }
                }

                double pocketAngle = Math.atan2(
                        bestPocket.y - bestTarget.y,
                        bestPocket.x - bestTarget.x
                );
                double ghostX = bestTarget.x - Math.cos(pocketAngle) * (bestTarget.radius * 2);
                double ghostY = bestTarget.y - Math.sin(pocketAngle) * (bestTarget.radius * 2);
                angle = Math.atan2(ghostY - cueBall.y, ghostX - cueBall.x);
                angle += (random.nextDouble() - 0.5) * 0.02;
                power = 17.0;
            }

            cueBall.vx = Math.cos(angle) * power;
            cueBall.vy = Math.sin(angle) * power;
        }
    }
}
