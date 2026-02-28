import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Medium-scope 2D Pokémon-like demo game built with Java Swing.
 *
 * Features:
 * - Tile-based overworld with walkable and blocking tiles.
 * - Player movement and camera follow.
 * - Wild grass encounters with turn-based battles.
 * - NPC trainer interaction and starter quest flavor text.
 * - Procedural pixel-art-like rendering (no external assets required).
 */
public class PokemonLikeGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Pocket Quest - Java 2D Demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new GamePanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

class GamePanel extends JPanel implements ActionListener, KeyListener {
    private static final int TILE_SIZE = 32;
    private static final int VIEW_COLS = 22;
    private static final int VIEW_ROWS = 16;
    private static final int WIDTH = VIEW_COLS * TILE_SIZE;
    private static final int HEIGHT = VIEW_ROWS * TILE_SIZE;

    private static final int MAP_W = 48;
    private static final int MAP_H = 36;

    private final Timer timer = new Timer(16, this); // ~60 FPS
    private final boolean[] keys = new boolean[256];
    private final Random random = new Random();

    private final int[][] map = new int[MAP_H][MAP_W];
    private final Player player = new Player(6, 7);
    private final List<Npc> npcs = new ArrayList<>();

    private GameState state = GameState.OVERWORLD;
    private BattleState battleState;

    private String dialogue = "Welcome to Pocket Quest! Move with arrows/WASD. Press E near NPCs.";
    private int dialogueTicks = 0;

    private enum GameState { OVERWORLD, BATTLE }

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);
        buildMap();
        spawnNpcs();
        timer.start();
    }

    private void buildMap() {
        // 0 grass, 1 path, 2 tree(block), 3 tall grass(encounter), 4 water(block), 5 house(block)
        for (int y = 0; y < MAP_H; y++) {
            for (int x = 0; x < MAP_W; x++) {
                map[y][x] = 0;
            }
        }

        // Borders and forest belt
        for (int x = 0; x < MAP_W; x++) {
            map[0][x] = 2;
            map[MAP_H - 1][x] = 2;
        }
        for (int y = 0; y < MAP_H; y++) {
            map[y][0] = 2;
            map[y][MAP_W - 1] = 2;
        }

        for (int x = 2; x < MAP_W - 2; x += 2) {
            map[4][x] = 2;
            if (x % 3 != 0) map[5][x] = 2;
            map[MAP_H - 5][x] = 2;
        }

        // Road network
        for (int x = 2; x < MAP_W - 2; x++) map[10][x] = 1;
        for (int y = 8; y < MAP_H - 2; y++) map[y][8] = 1;
        for (int y = 4; y < MAP_H - 6; y++) map[y][22] = 1;
        for (int x = 8; x < 23; x++) map[20][x] = 1;
        for (int x = 22; x < MAP_W - 6; x++) map[14][x] = 1;

        // Tall grass fields
        fillRect(12, 6, 7, 6, 3);
        fillRect(26, 9, 10, 6, 3);
        fillRect(14, 22, 11, 7, 3);
        fillRect(31, 19, 11, 9, 3);

        // Pond
        fillRect(35, 4, 8, 5, 4);

        // Houses
        fillRect(4, 14, 4, 3, 5);
        fillRect(18, 15, 4, 3, 5);
        fillRect(24, 24, 4, 3, 5);

        // Open doors on houses with path tiles
        map[16][5] = 1;
        map[17][5] = 1;
        map[17][19] = 1;
        map[26][25] = 1;
    }

    private void fillRect(int x0, int y0, int w, int h, int tile) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (x > 0 && y > 0 && x < MAP_W && y < MAP_H) {
                    map[y][x] = tile;
                }
            }
        }
    }

    private void spawnNpcs() {
        npcs.add(new Npc(9, 10, "Trainer Mia: Wild Pyromite appear in tall grass!", false));
        npcs.add(new Npc(22, 19, "Rival Ken: Beat 2 monsters and I'll duel you!", true));
        npcs.add(new Npc(7, 17, "Healer: Rest at town often. Potions are expensive!", false));
        npcs.add(new Npc(25, 26, "Professor Pine: Your starter is Leaflit. Raise it well!", false));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == GameState.OVERWORLD) {
            updateOverworld();
        } else if (state == GameState.BATTLE) {
            updateBattle();
        }
        repaint();
    }

    private void updateOverworld() {
        player.stepCooldown = Math.max(0, player.stepCooldown - 1);
        if (player.stepCooldown == 0) {
            int dx = 0;
            int dy = 0;
            if (isPressed(KeyEvent.VK_LEFT) || isPressed(KeyEvent.VK_A)) dx = -1;
            else if (isPressed(KeyEvent.VK_RIGHT) || isPressed(KeyEvent.VK_D)) dx = 1;
            else if (isPressed(KeyEvent.VK_UP) || isPressed(KeyEvent.VK_W)) dy = -1;
            else if (isPressed(KeyEvent.VK_DOWN) || isPressed(KeyEvent.VK_S)) dy = 1;

            if (dx != 0 || dy != 0) {
                tryMovePlayer(dx, dy);
                player.stepCooldown = 6;
            }
        }

        if (dialogueTicks > 0) dialogueTicks--;
    }

    private void updateBattle() {
        if (battleState == null) return;
        if (battleState.messageTicks > 0) {
            battleState.messageTicks--;
            return;
        }

        if (battleState.awaitingEnemyTurn) {
            enemyTurn();
            battleState.awaitingEnemyTurn = false;
        }
    }

    private void tryMovePlayer(int dx, int dy) {
        int nx = player.x + dx;
        int ny = player.y + dy;
        if (isBlocked(nx, ny)) return;

        for (Npc npc : npcs) {
            if (npc.x == nx && npc.y == ny) {
                return; // can't walk through NPC
            }
        }

        player.x = nx;
        player.y = ny;

        if (map[ny][nx] == 3 && random.nextDouble() < 0.08) {
            startWildBattle();
        }
    }

    private boolean isBlocked(int x, int y) {
        if (x < 0 || y < 0 || x >= MAP_W || y >= MAP_H) return true;
        int tile = map[y][x];
        return tile == 2 || tile == 4 || tile == 5;
    }

    private boolean isPressed(int keyCode) {
        return keyCode >= 0 && keyCode < keys.length && keys[keyCode];
    }

    private void startWildBattle() {
        int enemyHp = 18 + random.nextInt(14);
        int enemyLvl = 2 + random.nextInt(4);
        String[] species = {"Pyromite", "Aquafi", "Thorncub", "Voltlet"};
        String speciesName = species[random.nextInt(species.length)];

        battleState = new BattleState(
                "Leaflit", player.level, player.maxHp, player.hp,
                speciesName, enemyLvl, enemyHp, enemyHp,
                false
        );
        state = GameState.BATTLE;
        showDialogue("A wild " + speciesName + " appeared!");
    }

    private void startTrainerBattle(String trainerName) {
        int enemyHp = 24 + player.badges * 5;
        battleState = new BattleState(
                "Leaflit", player.level, player.maxHp, player.hp,
                "Rivalmon", player.level + 1, enemyHp, enemyHp,
                true
        );
        state = GameState.BATTLE;
        showDialogue(trainerName + " challenges you!");
    }

    private void playerAttack(int min, int max, String attackName) {
        if (battleState == null) return;
        int dmg = min + random.nextInt(max - min + 1) + player.level / 2;
        battleState.enemyHp = Math.max(0, battleState.enemyHp - dmg);
        battleState.message = battleState.playerName + " used " + attackName + "! (-" + dmg + ")";
        battleState.messageTicks = 45;

        if (battleState.enemyHp <= 0) {
            int expGain = 8 + battleState.enemyLevel * 3;
            player.exp += expGain;
            battleState.message = battleState.enemyName + " fainted! +" + expGain + " XP";
            battleState.messageTicks = 70;
            resolveLevelUp();

            if (battleState.trainerBattle) {
                player.badges = Math.max(player.badges, 1);
            }
            endBattle(true);
            return;
        }

        battleState.awaitingEnemyTurn = true;
    }

    private void enemyTurn() {
        if (battleState == null) return;
        int dmg = 3 + random.nextInt(6) + battleState.enemyLevel / 2;
        player.hp = Math.max(0, player.hp - dmg);
        battleState.playerHp = player.hp;
        battleState.message = battleState.enemyName + " struck back! (-" + dmg + ")";
        battleState.messageTicks = 55;

        if (player.hp <= 0) {
            battleState.message = "Leaflit fainted! You rushed to the nearest town.";
            battleState.messageTicks = 80;
            player.hp = player.maxHp;
            player.x = 6;
            player.y = 10;
            endBattle(false);
        }
    }

    private void resolveLevelUp() {
        int needed = player.level * 18;
        while (player.exp >= needed) {
            player.exp -= needed;
            player.level++;
            player.maxHp += 4;
            player.hp = player.maxHp;
            needed = player.level * 18;
        }
    }

    private void endBattle(boolean won) {
        Timer exitTimer = new Timer(700, ev -> {
            state = GameState.OVERWORLD;
            battleState = null;
            if (won) {
                showDialogue("Battle won! Keep training your Leaflit.");
            } else {
                showDialogue("You recovered at town. Stay sharp in tall grass!");
            }
        });
        exitTimer.setRepeats(false);
        exitTimer.start();
    }

    private void showDialogue(String text) {
        dialogue = text;
        dialogueTicks = 220;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (state == GameState.OVERWORLD) {
            drawOverworld(g2);
        } else {
            drawBattle(g2);
        }

        g2.dispose();
    }

    private void drawOverworld(Graphics2D g2) {
        int camX = clamp(player.x - VIEW_COLS / 2, 0, MAP_W - VIEW_COLS);
        int camY = clamp(player.y - VIEW_ROWS / 2, 0, MAP_H - VIEW_ROWS);

        for (int vy = 0; vy < VIEW_ROWS; vy++) {
            for (int vx = 0; vx < VIEW_COLS; vx++) {
                int mx = camX + vx;
                int my = camY + vy;
                int tile = map[my][mx];
                drawTile(g2, tile, vx * TILE_SIZE, vy * TILE_SIZE);
            }
        }

        for (Npc npc : npcs) {
            int sx = (npc.x - camX) * TILE_SIZE;
            int sy = (npc.y - camY) * TILE_SIZE;
            if (sx + TILE_SIZE >= 0 && sx < WIDTH && sy + TILE_SIZE >= 0 && sy < HEIGHT) {
                drawNpc(g2, npc, sx, sy);
            }
        }

        int playerSx = (player.x - camX) * TILE_SIZE;
        int playerSy = (player.y - camY) * TILE_SIZE;
        drawPlayer(g2, playerSx, playerSy);

        drawHud(g2);
    }

    private void drawBattle(Graphics2D g2) {
        g2.setPaint(new GradientPaint(0, 0, new Color(130, 205, 255), 0, HEIGHT, new Color(85, 155, 110)));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        // Enemy platform and monster
        g2.setColor(new Color(90, 145, 95));
        g2.fillOval(WIDTH - 280, 95, 180, 50);
        drawMonster(g2, WIDTH - 210, 62, false);

        // Player platform and monster
        g2.setColor(new Color(90, 145, 95));
        g2.fillOval(90, 250, 210, 60);
        drawMonster(g2, 180, 214, true);

        drawBattleBox(g2, WIDTH - 320, 40, battleState.enemyName, battleState.enemyLevel,
                battleState.enemyHp, battleState.enemyMaxHp);
        drawBattleBox(g2, 30, 170, battleState.playerName, battleState.playerLevel,
                battleState.playerHp, battleState.playerMaxHp);

        // message and controls
        g2.setColor(new Color(30, 30, 42, 220));
        g2.fillRoundRect(20, HEIGHT - 150, WIDTH - 40, 130, 16, 16);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Dialog", Font.BOLD, 18));
        g2.drawString(battleState.message == null ? "Choose an action" : battleState.message, 35, HEIGHT - 108);

        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.drawString("[1] Leaf Slash", 40, HEIGHT - 70);
        g2.drawString("[2] Vine Whip", 240, HEIGHT - 70);
        g2.drawString("[3] Heal", 450, HEIGHT - 70);
        g2.drawString("[4] Run", 570, HEIGHT - 70);
    }

    private void drawTile(Graphics2D g2, int tile, int x, int y) {
        switch (tile) {
            case 1 -> {
                g2.setColor(new Color(206, 182, 124));
                g2.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                g2.setColor(new Color(186, 164, 111));
                g2.drawLine(x, y + 8, x + TILE_SIZE, y + 8);
            }
            case 2 -> {
                g2.setColor(new Color(40, 128, 70));
                g2.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                g2.setColor(new Color(27, 92, 46));
                g2.fillOval(x + 4, y + 2, 24, 24);
                g2.setColor(new Color(77, 56, 38));
                g2.fillRect(x + 13, y + 18, 6, 12);
            }
            case 3 -> {
                g2.setColor(new Color(75, 192, 88));
                g2.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                g2.setColor(new Color(40, 120, 50));
                for (int i = 0; i < 4; i++) {
                    g2.drawLine(x + 4 + i * 7, y + TILE_SIZE, x + 8 + i * 7, y + 17);
                }
            }
            case 4 -> {
                g2.setColor(new Color(60, 132, 224));
                g2.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                g2.setColor(new Color(120, 180, 245, 180));
                g2.drawArc(x + 4, y + 9, 20, 12, 0, 180);
                g2.drawArc(x + 10, y + 18, 20, 12, 0, 180);
            }
            case 5 -> {
                g2.setColor(new Color(158, 96, 72));
                g2.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                g2.setColor(new Color(120, 62, 52));
                g2.fillRect(x + 4, y + 12, 24, 20);
                g2.setColor(new Color(220, 180, 145));
                g2.fillRect(x + 12, y + 18, 8, 14);
            }
            default -> {
                g2.setColor(new Color(96, 202, 96));
                g2.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                g2.setColor(new Color(86, 186, 86));
                g2.fillRect(x, y + 20, TILE_SIZE, 12);
            }
        }
    }

    private void drawPlayer(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(40, 72, 205));
        g2.fillRoundRect(x + 9, y + 8, 14, 16, 4, 4);
        g2.setColor(new Color(235, 220, 195));
        g2.fillOval(x + 10, y + 4, 12, 10);
        g2.setColor(Color.RED);
        g2.fillRect(x + 8, y + 2, 16, 5);
        g2.setColor(Color.BLACK);
        g2.drawRect(x + 8, y + 2, 16, 5);
    }

    private void drawNpc(Graphics2D g2, Npc npc, int x, int y) {
        g2.setColor(npc.trainerBattle ? new Color(197, 66, 72) : new Color(120, 82, 188));
        g2.fillRoundRect(x + 9, y + 9, 14, 15, 5, 5);
        g2.setColor(new Color(238, 218, 192));
        g2.fillOval(x + 10, y + 4, 12, 10);
        g2.setColor(Color.BLACK);
        g2.drawOval(x + 10, y + 4, 12, 10);
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(new Color(20, 20, 26, 210));
        g2.fillRoundRect(10, 10, 245, 95, 14, 14);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Dialog", Font.BOLD, 14));
        g2.drawString("Leaflit Lv." + player.level + "  HP " + player.hp + "/" + player.maxHp, 20, 35);
        g2.drawString("XP: " + player.exp + "    Badges: " + player.badges, 20, 56);
        g2.drawString("Goal: Beat Rival Ken", 20, 77);

        if (dialogueTicks > 0) {
            g2.setColor(new Color(18, 16, 31, 220));
            g2.fillRoundRect(10, HEIGHT - 88, WIDTH - 20, 78, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Dialog", Font.BOLD, 16));
            g2.drawString(dialogue, 22, HEIGHT - 43);
        }
    }

    private void drawMonster(Graphics2D g2, int x, int y, boolean playerMon) {
        Color body = playerMon ? new Color(88, 210, 122) : new Color(255, 168, 94);
        Color accent = playerMon ? new Color(51, 136, 72) : new Color(190, 100, 35);
        g2.setColor(body);
        g2.fillOval(x - 35, y - 22, 70, 48);
        g2.setColor(accent);
        g2.fillOval(x - 16, y - 37, 32, 22);
        g2.setColor(Color.WHITE);
        g2.fillOval(x - 16, y - 10, 10, 10);
        g2.fillOval(x + 6, y - 10, 10, 10);
        g2.setColor(Color.BLACK);
        g2.fillOval(x - 12, y - 7, 4, 4);
        g2.fillOval(x + 10, y - 7, 4, 4);
    }

    private void drawBattleBox(Graphics2D g2, int x, int y, String name, int level, int hp, int maxHp) {
        g2.setColor(new Color(245, 245, 245, 238));
        g2.fillRoundRect(x, y, 280, 80, 12, 12);
        g2.setColor(new Color(22, 22, 22));
        g2.setFont(new Font("Dialog", Font.BOLD, 16));
        g2.drawString(name + "  Lv." + level, x + 14, y + 24);

        int barX = x + 15;
        int barY = y + 38;
        int barW = 220;
        g2.setColor(new Color(60, 60, 60));
        g2.fillRect(barX, barY, barW, 16);

        float ratio = Math.max(0f, Math.min(1f, hp / (float) maxHp));
        Color hpColor = ratio > 0.5f ? new Color(67, 196, 76) : ratio > 0.2f ? new Color(240, 190, 70) : new Color(225, 79, 79);
        g2.setColor(hpColor);
        g2.fillRect(barX + 2, barY + 2, (int) ((barW - 4) * ratio), 12);

        g2.setColor(new Color(30, 30, 30));
        g2.setFont(new Font("Dialog", Font.PLAIN, 13));
        g2.drawString(hp + " / " + maxHp, x + 170, y + 66);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= 0 && code < keys.length) {
            keys[code] = true;
        }

        if (state == GameState.OVERWORLD) {
            if (code == KeyEvent.VK_E) {
                interactWithNpc();
            }
        } else if (state == GameState.BATTLE && battleState != null && battleState.messageTicks == 0) {
            switch (code) {
                case KeyEvent.VK_1 -> playerAttack(5, 9, "Leaf Slash");
                case KeyEvent.VK_2 -> playerAttack(7, 11, "Vine Whip");
                case KeyEvent.VK_3 -> usePotion();
                case KeyEvent.VK_4 -> attemptRun();
                default -> {}
            }
        }
    }

    private void interactWithNpc() {
        for (Npc npc : npcs) {
            if (Math.abs(npc.x - player.x) + Math.abs(npc.y - player.y) == 1) {
                showDialogue(npc.text);
                if (npc.trainerBattle && player.badges == 0) {
                    startTrainerBattle("Rival Ken");
                }
                return;
            }
        }
        showDialogue("Nobody is close enough to talk.");
    }

    private void usePotion() {
        if (battleState == null) return;
        int heal = 8 + random.nextInt(7);
        player.hp = Math.min(player.maxHp, player.hp + heal);
        battleState.playerHp = player.hp;
        battleState.message = "You used a potion! ( +" + heal + " HP )";
        battleState.messageTicks = 45;
        battleState.awaitingEnemyTurn = true;
    }

    private void attemptRun() {
        if (battleState == null) return;
        if (battleState.trainerBattle) {
            battleState.message = "Can't run from a trainer battle!";
            battleState.messageTicks = 45;
            return;
        }

        boolean escaped = random.nextDouble() < 0.55;
        battleState.message = escaped ? "You escaped safely." : "Couldn't escape!";
        battleState.messageTicks = 45;
        if (escaped) {
            endBattle(false);
        } else {
            battleState.awaitingEnemyTurn = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= 0 && code < keys.length) {
            keys[code] = false;
        }
    }

    static class Player {
        int x;
        int y;
        int level = 5;
        int exp = 0;
        int badges = 0;
        int hp = 34;
        int maxHp = 34;
        int stepCooldown = 0;

        Player(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static class Npc {
        final int x;
        final int y;
        final String text;
        final boolean trainerBattle;

        Npc(int x, int y, String text, boolean trainerBattle) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.trainerBattle = trainerBattle;
        }
    }

    static class BattleState {
        final String playerName;
        int playerLevel;
        int playerHp;
        int playerMaxHp;

        final String enemyName;
        int enemyLevel;
        int enemyHp;
        int enemyMaxHp;

        final boolean trainerBattle;

        String message;
        int messageTicks;
        boolean awaitingEnemyTurn;

        BattleState(String playerName, int playerLevel, int playerMaxHp, int playerHp,
                    String enemyName, int enemyLevel, int enemyMaxHp, int enemyHp,
                    boolean trainerBattle) {
            this.playerName = playerName;
            this.playerLevel = playerLevel;
            this.playerMaxHp = playerMaxHp;
            this.playerHp = playerHp;
            this.enemyName = enemyName;
            this.enemyLevel = enemyLevel;
            this.enemyMaxHp = enemyMaxHp;
            this.enemyHp = enemyHp;
            this.trainerBattle = trainerBattle;
            this.message = "Choose an action.";
            this.messageTicks = 0;
            this.awaitingEnemyTurn = false;
        }
    }
}
