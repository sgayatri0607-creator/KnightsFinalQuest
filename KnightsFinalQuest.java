import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * KNIGHT'S FINAL QUEST - a 2D fantasy RPG in Java Swing / Java 2D.
 *
 * Flow: Menu -> Character Creation -> Intro -> Level 1 (Lost Ruins maze)
 *       -> Potion Shop -> Level 2 (Training Grounds, Goblin Captain)
 *       -> Level 3 (Skybound Trials parkour) -> Level 4 (Dark Fortress, Dark Lord)
 *       -> Victory / Game Over
 *
 * Compile: javac KnightsFinalQuest.java
 * Run:     java KnightsFinalQuest
 *
 * Controls: WASD / Arrow keys to move, SPACE to jump (Level 3), ESC to pause,
 *           mouse to click buttons, click or SPACE to advance story text,
 *           N to mute / unmute sound.
 */
public class KnightsFinalQuest extends JPanel
        implements ActionListener, KeyListener, MouseListener, MouseMotionListener {

    // ======================= CONSTANTS =======================
    static final int W = 800, H = 600;
    static final int TS = 32, COLS = 25, ROWS = 17;          // maze tile size / grid
    static final int WORLD = 3200, PW = 24, PH = 32;         // platformer world / player size

    static final Color[] OUTFIT_COLORS = {
        new Color(45, 45, 55), new Color(135, 65, 185), new Color(240, 120, 170), new Color(60, 120, 225) };
    static final String[] OUTFIT_NAMES = { "Black", "Purple", "Pink", "Blue" };
    static final Color[] HAIR_COLORS = {
        new Color(110, 70, 30), new Color(235, 200, 90), new Color(200, 90, 30), new Color(25, 20, 25) };
    static final String[] HAIR_NAMES = { "Brown", "Blonde", "Ginger", "Black" };

    enum State { MENU, CREATION, INTRO, LEVEL1, SHOP, LEVEL2, LEVEL3,
                 DIALOGUE4, LEVEL4, VICTORY, GAME_OVER }

    // ======================= DATA CLASSES =======================
    /** Everything about the player that persists across levels. */
    static class Player {
        String name = "";
        int armor = 0, hair = 0;
        int hp = 5, maxHp = 10, attack = 2, coins = 0, potions = 0, treasures = 0;
        boolean hasSword = false, hasKey = false;
    }

    /** A clickable on-screen button. */
    static class Button {
        Rectangle r;
        Supplier<String> text;
        Runnable action;
        BooleanSupplier enabled = () -> true;
        BooleanSupplier selected = () -> false;

        Button(int x, int y, int w, int h, String t, Runnable a) {
            r = new Rectangle(x, y, w, h); text = () -> t; action = a;
        }
        Button(int x, int y, int w, int h, Supplier<String> t, Runnable a) {
            r = new Rectangle(x, y, w, h); text = t; action = a;
        }
    }

    /** A platform in the parkour level (optionally moving left/right). */
    static class Plat {
        double x, y, w, minX, maxX, speed, dx;
        int dir = 1;
        Plat(double x, double y, double w) { this.x = x; this.y = y; this.w = w; }
        boolean moving() { return maxX > 0; }
    }

    /** Procedurally generated sound effects and music (no audio files needed). */
    static class Sound {
        static final float RATE = 22050f;
        static boolean muted = false;
        static Clip music;
        static String musicName = "";
        static final Map<String, byte[]> SFX = new HashMap<>();
        static final Map<String, byte[]> TRACKS = new HashMap<>();

        static {
            // sfx(name, wave 0=sine 1=square 2=noise, volume, {{frequency Hz, length ms}, ...})
            sfx("click", 0, 0.30, new double[][]{{700, 30}});
            sfx("coin", 1, 0.25, new double[][]{{988, 70}, {1319, 150}});
            sfx("pickup", 0, 0.40, new double[][]{{523, 90}, {659, 90}, {784, 90}, {1047, 220}});
            sfx("unlock", 0, 0.40, new double[][]{{392, 80}, {523, 160}});
            sfx("hurt", 1, 0.30, new double[][]{{220, 110}, {150, 200}});
            sfx("jump", 0, 0.30, new double[][]{{300, 40}, {450, 50}, {600, 60}});
            sfx("checkpoint", 0, 0.40, new double[][]{{660, 80}, {880, 80}, {1100, 180}});
            sfx("gem", 0, 0.35, new double[][]{{1200, 50}, {1500, 120}});
            sfx("hit", 2, 0.45, new double[][]{{1, 90}});
            sfx("defend", 0, 0.40, new double[][]{{300, 60}, {250, 110}});
            sfx("heal", 0, 0.40, new double[][]{{523, 80}, {659, 80}, {784, 140}});
            sfx("special", 1, 0.25, new double[][]{{400, 60}, {600, 60}, {800, 60}, {1000, 60}, {1400, 180}});
            sfx("phase", 1, 0.35, new double[][]{{120, 200}, {90, 300}, {120, 300}});
            sfx("win", 0, 0.45, new double[][]{{523, 150}, {659, 150}, {784, 150}, {1047, 450}});
            sfx("lose", 0, 0.45, new double[][]{{392, 250}, {330, 250}, {262, 600}});

            // track(name, notes as semitones from A4 (99 = rest), ms per note, wave, volume)
            track("menu", new int[]{-12, -9, -7, -5, -2, -5, -7, -9, -12, -9, -5, -2, 0, -2, -5, -9}, 380, 0, 0.30);
            track("ruins", new int[]{-12, 99, -9, 99, -12, 99, -5, 99, -7, 99, -9, 99, -12, 99, 99, 99}, 420, 0, 0.30);
            track("shop", new int[]{0, 3, 5, 3, 7, 5, 3, 0, 5, 7, 10, 7, 5, 3, 0, -2}, 200, 0, 0.30);
            track("combat", new int[]{-12, -12, -9, -12, -7, -12, -9, -5, -12, -12, -9, -12, -5, -7, -9, -12}, 220, 1, 0.14);
            track("parkour", new int[]{0, 5, 7, 12, 7, 5, 0, 3, 7, 10, 7, 3, 0, 5, 7, 12, 10, 7, 5, 3}, 180, 0, 0.30);
            track("boss", new int[]{-24, -24, -21, -24, -19, -24, -17, -19, -24, -24, -21, -24, -14, -17, -19, -21}, 160, 1, 0.16);
        }

        static void sfx(String name, int wave, double vol, double[][] notes) {
            SFX.put(name, synth(notes, vol, wave));
        }

        static void track(String name, int[] semis, int ms, int wave, double vol) {
            double[][] notes = new double[semis.length][2];
            for (int i = 0; i < semis.length; i++) {
                notes[i][0] = semis[i] == 99 ? 0 : 440.0 * Math.pow(2, semis[i] / 12.0);
                notes[i][1] = ms;
            }
            TRACKS.put(name, synth(notes, vol, wave));
        }

        /** Builds 16-bit mono PCM audio from a list of {frequency, milliseconds} notes. */
        static byte[] synth(double[][] notes, double vol, int wave) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Random noise = new Random(1);
            for (double[] n : notes) {
                int samples = (int) (RATE * n[1] / 1000.0);
                for (int i = 0; i < samples; i++) {
                    double t = i / (double) RATE;
                    double env = Math.max(0, Math.min(Math.min(1.0, i / 150.0), (samples - i) / (RATE * 0.03)));
                    double v;
                    if (n[0] == 0) v = 0;
                    else if (wave == 1) v = Math.sin(2 * Math.PI * n[0] * t) >= 0 ? 0.6 : -0.6;
                    else if (wave == 2) v = noise.nextDouble() * 2 - 1;
                    else v = Math.sin(2 * Math.PI * n[0] * t);
                    short sample = (short) (v * env * vol * Short.MAX_VALUE);
                    out.write(sample & 0xFF);
                    out.write((sample >> 8) & 0xFF);
                }
            }
            return out.toByteArray();
        }

        static Clip makeClip(byte[] data) throws Exception {
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            Clip c = AudioSystem.getClip();
            c.open(fmt, data, 0, data.length);
            return c;
        }

        static void setGain(Clip c, float db) {
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl fc = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                fc.setValue(Math.max(fc.getMinimum(), Math.min(fc.getMaximum(), db)));
            }
        }

        /** Plays a short sound effect. Fails silently if the computer has no audio device. */
        static void play(String name) {
            if (muted) return;
            byte[] d = SFX.get(name);
            if (d == null) return;
            try {
                final Clip c = makeClip(d);
                c.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) c.close(); });
                c.start();
            } catch (Exception ex) { /* no sound available - ignore */ }
        }

        /** Switches background music ("" = silence). Does nothing if that track is already playing. */
        static void music(String name) {
            if (name.equals(musicName)) return;
            stopMusic();
            musicName = name;
            if (muted || name.isEmpty()) return;
            byte[] d = TRACKS.get(name);
            if (d == null) return;
            try {
                music = makeClip(d);
                setGain(music, -8f);
                music.loop(Clip.LOOP_CONTINUOUSLY);
            } catch (Exception ex) { music = null; }
        }

        static void stopMusic() {
            if (music != null) { music.stop(); music.close(); music = null; }
        }

        static void toggleMute() {
            muted = !muted;
            if (muted) stopMusic();
            else { String n = musicName; musicName = ""; music(n); }
        }
    }

    // ======================= GAME FIELDS =======================
    State state = State.MENU;
    Player p = new Player();
    Random rnd = new Random();
    int tick = 0;
    boolean left, right, up, down, space, paused;
    int mouseX, mouseY;
    List<Button> buttons = new ArrayList<>();
    String message = "";
    int messageTimer = 0;

    // dialogue
    int dlgIdx = 0;

    // Level 1
    char[][] map;
    double px, py;
    int invuln = 0;

    // Shop
    String shopMsg = "";

    // Combat
    boolean finalBoss, combatOver, playerDead, defending, retrying;
    String eName = "";
    int eHp, eMax, phase, eTurns, specialCd, enemyDelay, endTimer;
    int shakeEnemy, shakePlayer, bannerTimer;
    String banner = "";
    int sHp, sPot, sCoins;                       // snapshot for Retry
    List<String> logLines = new ArrayList<>();

    // Level 3 (platformer)
    List<Plat> plats = new ArrayList<>();
    List<Rectangle> ladders = new ArrayList<>();
    List<Rectangle> spikes = new ArrayList<>();
    List<double[]> gems = new ArrayList<>();     // {x, y, collected}
    int[][] checkpoints = { {60, 460}, {890, 340}, {1120, 460}, {2120, 400}, {2880, 380} };
    int cp = 0;
    double vy, camX;
    boolean onGround, climbing;
    Plat standing;

    // ======================= STARTUP =======================
    public KnightsFinalQuest() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        setState(State.MENU);
        new javax.swing.Timer(16, this).start();   // ~60 FPS game loop
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Knight's Final Quest");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setResizable(false);
            KnightsFinalQuest game = new KnightsFinalQuest();
            f.add(game);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            game.requestFocusInWindow();
        });
    }

    // ======================= STATE MANAGEMENT =======================
    void setState(State s) {
        state = s;
        Sound.music(trackFor(s));
        if (s == State.VICTORY) Sound.play("win");
        if (s == State.GAME_OVER) Sound.play("lose");
        buttons.clear();
        paused = false;
        left = right = up = down = space = false;
        dlgIdx = 0;
        switch (s) {
            case MENU:
                buttons.add(new Button(300, 330, 200, 50, "START GAME", () -> {
                    p.name = ""; setState(State.CREATION); }));
                buttons.add(new Button(300, 400, 200, 50, "QUIT", () -> System.exit(0)));
                break;
            case CREATION: buildCreation(); break;
            case LEVEL1: initLevel1(); break;
            case SHOP: buildShop(); break;
            case LEVEL2: startCombat(false); break;
            case LEVEL3: initPlatformer(); break;
            case LEVEL4: startCombat(true); break;
            case VICTORY:
                buttons.add(new Button(300, 440, 200, 50, "PLAY AGAIN", () -> {
                    startNewGame(); setState(State.INTRO); }));
                buttons.add(new Button(300, 505, 200, 50, "MAIN MENU", () -> setState(State.MENU)));
                break;
            case GAME_OVER:
                buttons.add(new Button(300, 340, 200, 50, "RETRY", () -> {
                    retrying = true; setState(finalBoss ? State.LEVEL4 : State.LEVEL2); }));
                buttons.add(new Button(300, 410, 200, 50, "MAIN MENU", () -> setState(State.MENU)));
                break;
            default: break;
        }
    }

    void startNewGame() {
        p.hp = 5; p.maxHp = 10; p.attack = 2;
        p.coins = 0; p.potions = 0; p.treasures = 0;
        p.hasSword = false; p.hasKey = false;
    }

    void flash(String s) { message = s; messageTimer = 140; }

    String trackFor(State s) {
        switch (s) {
            case MENU: case CREATION: case INTRO: return "menu";
            case LEVEL1: return "ruins";
            case SHOP: return "shop";
            case LEVEL2: return "combat";
            case LEVEL3: return "parkour";
            case DIALOGUE4: case LEVEL4: return "boss";
            default: return "";
        }
    }

    // ======================= CHARACTER CREATION =======================
    void buildCreation() {
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button a = new Button(40 + i * 108, 230, 100, 44, OUTFIT_NAMES[i], () -> p.armor = idx);
            a.selected = () -> p.armor == idx;
            buttons.add(a);
            Button h = new Button(40 + i * 108, 330, 100, 44, HAIR_NAMES[i], () -> p.hair = idx);
            h.selected = () -> p.hair == idx;
            buttons.add(h);
        }
        buttons.add(new Button(260, 500, 280, 50, "BEGIN JOURNEY", () -> {
            if (p.name.trim().isEmpty()) p.name = "Knight";
            startNewGame();
            setState(State.INTRO);
        }));
    }

    // ======================= LEVEL 1: LOST RUINS (MAZE) =======================
    void initLevel1() {
        map = new char[ROWS][COLS];
        for (char[] row : map) Arrays.fill(row, '#');

        // Randomised depth-first maze on a 12 x 8 cell grid (guaranteed solvable).
        boolean[][] vis = new boolean[8][12];
        Deque<int[]> st = new ArrayDeque<>();
        vis[0][0] = true;
        map[1][1] = '.';
        st.push(new int[]{0, 0});
        int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1};
        while (!st.isEmpty()) {
            int[] cur = st.peek();
            List<Integer> opts = new ArrayList<>();
            for (int d = 0; d < 4; d++) {
                int nr = cur[0] + dr[d], nc = cur[1] + dc[d];
                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 12 && !vis[nr][nc]) opts.add(d);
            }
            if (opts.isEmpty()) { st.pop(); continue; }
            int d = opts.get(rnd.nextInt(opts.size()));
            int nr = cur[0] + dr[d], nc = cur[1] + dc[d];
            vis[nr][nc] = true;
            map[nr * 2 + 1][nc * 2 + 1] = '.';
            map[cur[0] * 2 + 1 + dr[d]][cur[1] * 2 + 1 + dc[d]] = '.';
            st.push(new int[]{nr, nc});
        }

        // Knock out some extra walls so the maze has loops (gives alternative routes)
        int removed = 0, tries = 0;
        while (removed < 25 && tries < 600) {
            tries++;
            int r = 1 + rnd.nextInt(ROWS - 2), c = 1 + rnd.nextInt(COLS - 2);
            if (map[r][c] != '#') continue;
            boolean horiz = (r % 2 == 1 && c % 2 == 0), vert = (r % 2 == 0 && c % 2 == 1);
            if (horiz && map[r][c - 1] == '.' && map[r][c + 1] == '.') { map[r][c] = '.'; removed++; }
            else if (vert && map[r - 1][c] == '.' && map[r + 1][c] == '.') { map[r][c] = '.'; removed++; }
        }

        // Distances from the start (BFS)
        int[][] dist = new int[ROWS][COLS];
        for (int[] row : dist) Arrays.fill(row, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{1, 1});
        dist[1][1] = 0;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int d = 0; d < 4; d++) {
                int nr = c[0] + dr[d], nc = c[1] + dc[d];
                if (map[nr][nc] == '.' && dist[nr][nc] == -1) {
                    dist[nr][nc] = dist[c[0]][c[1]] + 1;
                    q.add(new int[]{nr, nc});
                }
            }
        }

        // Exit = farthest dead end, with a locked door on its only entrance
        int er = 1, ec = 1, best = -1;
        for (int r = 1; r < ROWS - 1; r++) {
            for (int c = 1; c < COLS - 1; c++) {
                if (map[r][c] != '.' || (r == 1 && c == 1)) continue;
                int n = 0;
                for (int d = 0; d < 4; d++) if (map[r + dr[d]][c + dc[d]] == '.') n++;
                if (n == 1 && dist[r][c] > best) { best = dist[r][c]; er = r; ec = c; }
            }
        }
        for (int d = 0; d < 4; d++) {
            if (map[er + dr[d]][ec + dc[d]] == '.') { map[er + dr[d]][ec + dc[d]] = 'D'; break; }
        }
        map[er][ec] = 'E';

        // Scatter items on free floor tiles
        List<int[]> cand = new ArrayList<>();
        for (int r = 1; r < ROWS - 1; r++)
            for (int c = 1; c < COLS - 1; c++)
                if (map[r][c] == '.' && dist[r][c] >= 4) cand.add(new int[]{r, c, dist[r][c]});
        Collections.shuffle(cand, rnd);
        put(cand, 'S', 1, 14);     // Legendary Sword (far from start)
        put(cand, 'K', 1, 14);     // Key
        put(cand, 'C', 10, 0);     // exactly 10 coins
        put(cand, 'P', 2, 0);      // free healing potions
        put(cand, 'T', 3, 0);      // treasures
        placeSpikes(cand, 6);      // spike traps (always avoidable)

        px = TS + 5; py = TS + 5;
        invuln = 0;
        p.hasKey = false;
        flash("Find the Legendary Sword, the Key and escape the ruins!");
    }

    void put(List<int[]> cand, char ch, int count, int minDist) {
        for (int i = 0; i < count; i++) {
            int pick = -1;
            for (int j = 0; j < cand.size(); j++) if (cand.get(j)[2] >= minDist) { pick = j; break; }
            if (pick == -1) pick = 0;
            if (cand.isEmpty()) return;
            int[] c = cand.remove(pick);
            map[c[0]][c[1]] = ch;
        }
    }

    /** Places spikes only where every item, the door and the exit stay reachable without touching one. */
    void placeSpikes(List<int[]> cand, int count) {
        int placed = 0;
        for (int[] c : cand) {
            if (placed >= count) break;
            if (map[c[0]][c[1]] != '.') continue;
            map[c[0]][c[1]] = '^';
            if (itemsReachable()) placed++;
            else map[c[0]][c[1]] = '.';       // this spike would block a route - skip it
        }
    }

    boolean itemsReachable() {
        boolean[][] seen = new boolean[ROWS][COLS];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{1, 1});
        seen[1][1] = true;
        int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int d = 0; d < 4; d++) {
                int nr = c[0] + dr[d], nc = c[1] + dc[d];
                if (nr < 0 || nc < 0 || nr >= ROWS || nc >= COLS || seen[nr][nc]) continue;
                if (map[nr][nc] == '#' || map[nr][nc] == '^') continue;
                seen[nr][nc] = true;
                q.add(new int[]{nr, nc});
            }
        }
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if ("SKCPTDE".indexOf(map[r][c]) >= 0 && !seen[r][c]) return false;
        return true;
    }

    boolean blocked(double x, double y) {
        int s = 22;
        double[][] pts = { {x, y}, {x + s - 1, y}, {x, y + s - 1}, {x + s - 1, y + s - 1} };
        for (double[] pt : pts) {
            int c = (int) (pt[0] / TS), r = (int) (pt[1] / TS);
            if (r < 0 || c < 0 || r >= ROWS || c >= COLS) return true;
            char ch = map[r][c];
            if (ch == '#') return true;
            if (ch == 'D' && !p.hasKey) { flash("The door is locked. Find the key!"); return true; }
        }
        return false;
    }

    void updateLevel1() {
        int dx = 0, dy = 0;
        if (left) dx -= 3;
        if (right) dx += 3;
        if (up) dy -= 3;
        if (down) dy += 3;
        for (int i = 0; i < Math.abs(dx); i++) if (!blocked(px + Math.signum(dx), py)) px += Math.signum(dx);
        for (int i = 0; i < Math.abs(dy); i++) if (!blocked(px, py + Math.signum(dy))) py += Math.signum(dy);
        if (invuln > 0) invuln--;

        int c = (int) ((px + 11) / TS), r = (int) ((py + 11) / TS);
        switch (map[r][c]) {
            case 'C': p.coins++; map[r][c] = '.'; Sound.play("coin"); flash("+1 coin  (" + p.coins + "/10)"); break;
            case 'S': p.hasSword = true; map[r][c] = '.'; Sound.play("pickup"); flash("You found the LEGENDARY SWORD!"); break;
            case 'P': p.potions++; map[r][c] = '.'; Sound.play("pickup"); flash("Healing potion collected!"); break;
            case 'T': p.treasures++; p.hp = Math.min(p.maxHp, p.hp + 1); map[r][c] = '.'; Sound.play("pickup");
                      flash("Treasure found! +1 HP"); break;
            case 'K': p.hasKey = true; map[r][c] = '.'; Sound.play("pickup"); flash("You got the KEY!"); break;
            case 'D': map[r][c] = '.'; Sound.play("unlock"); flash("Door unlocked!"); break;
            case '^':
                if (invuln == 0) {
                    p.hp = Math.max(1, p.hp - 1);
                    invuln = 60;
                    Sound.play("hurt");
                    flash("Spikes! -1 HP");
                }
                break;
            case 'E':
                if (p.hasSword) { setState(State.SHOP); return; }
                flash("You need the Legendary Sword to leave!");
                break;
            default: break;
        }
    }

    // ======================= POTION SHOP =======================
    void buildShop() {
        shopMsg = "Welcome, traveller! Spend your coins wisely.";
        int[][] opts = { {3, 1}, {6, 5}, {10, 10} };
        String[] names = { "Small Potion (+1 HP)", "Half-Heal Potion (+5 HP)", "Full Heal Potion (FULL HP)" };
        for (int i = 0; i < 3; i++) {
            final int cost = opts[i][0], heal = opts[i][1];
            final String nm = names[i];
            Button b = new Button(150, 190 + i * 80, 500, 60,
                    () -> nm + " - " + cost + " coins" + shopStatus(cost),
                    () -> buy(cost, heal));
            b.enabled = () -> p.coins >= cost && p.hp < p.maxHp;
            buttons.add(b);
        }
        buttons.add(new Button(260, 470, 280, 50, "LEAVE SHOP", () -> setState(State.LEVEL2)));
    }

    String shopStatus(int cost) {
        if (p.hp >= p.maxHp) return "  [HP FULL]";
        return p.coins >= cost ? "  [AVAILABLE]" : "  [LOCKED]";
    }

    void buy(int cost, int heal) {
        if (p.coins < cost || p.hp >= p.maxHp) return;
        p.coins -= cost;
        p.hp = Math.min(p.maxHp, p.hp + heal);       // HP can never exceed max
        Sound.play("coin");
        shopMsg = "Potion purchased! Coins remaining: " + p.coins;
    }

    // ======================= COMBAT (LEVEL 2 + LEVEL 4) =======================
    void startCombat(boolean boss) {
        finalBoss = boss;
        if (retrying) { p.hp = sHp; p.potions = sPot; p.coins = sCoins; retrying = false; }
        else { sHp = p.hp; sPot = p.potions; sCoins = p.coins; }

        eName = boss ? "DARK LORD" : "Goblin Captain";
        eMax = eHp = boss ? 20 : 8;
        phase = 1; eTurns = 0; specialCd = 0;
        defending = false; combatOver = false; playerDead = false;
        enemyDelay = 0; endTimer = 0; shakeEnemy = shakePlayer = bannerTimer = 0;
        logLines.clear();
        log(boss ? "The Dark Lord rises from his throne!" : "A Goblin Captain blocks your path!");

        String[] labels = { "ATTACK", "DEFEND", "HEAL", "SPECIAL" };
        for (int i = 0; i < 4; i++) {
            final int a = i;
            Supplier<String> t = () -> {
                if (a == 2) return "HEAL (" + p.potions + ")";
                if (a == 3) return specialCd > 0 ? "SPECIAL (" + specialCd + ")" : "SPECIAL";
                return labels[a];
            };
            Button b = new Button(40 + i * 190, 515, 175, 50, t, () -> combatAction(a));
            b.enabled = () -> {
                if (combatOver || enemyDelay > 0) return false;
                if (a == 2) return p.potions > 0 && p.hp < p.maxHp;
                if (a == 3) return p.hasSword && specialCd == 0;
                return true;
            };
            buttons.add(b);
        }
    }

    void log(String s) {
        logLines.add(s);
        if (logLines.size() > 5) logLines.remove(0);
    }

    void combatAction(int a) {
        if (combatOver || enemyDelay > 0) return;
        switch (a) {
            case 0: Sound.play("hit"); damageEnemy(p.attack, "You strike for " + p.attack + " damage!"); break;
            case 1: Sound.play("defend"); defending = true; log("You raise your shield!"); break;
            case 2: {
                p.potions--;
                int before = p.hp;
                p.hp = Math.min(p.maxHp, p.hp + 4);
                Sound.play("heal");
                log("You drink a potion. +" + (p.hp - before) + " HP");
                break;
            }
            case 3: {
                int d = p.attack * 2 + 1;
                specialCd = 3;
                Sound.play("special");
                damageEnemy(d, "SPECIAL ATTACK! " + d + " damage!");
                break;
            }
            default: break;
        }
        if (eHp <= 0) {
            combatOver = true;
            endTimer = 70;
            log(eName + " is defeated!");
            if (!finalBoss) Sound.play("win");
            return;
        }
        enemyDelay = 45;      // short pause, then the enemy acts
    }

    void damageEnemy(int d, String msg) {
        eHp = Math.max(0, eHp - d);
        shakeEnemy = 12;
        log(msg);
        if (finalBoss && eHp > 0) {                 // boss phases
            if (eHp <= eMax / 4 && phase < 3) {
                phase = 3; banner = "FINAL STAND!"; bannerTimer = 90; Sound.play("phase");
                log("The Dark Lord unleashes his special attack!");
            } else if (eHp <= eMax / 2 && phase < 2) {
                phase = 2; banner = "YOU DARE CHALLENGE ME?!"; bannerTimer = 90; Sound.play("phase");
                log("The Dark Lord is ENRAGED!");
            }
        }
    }

    void enemyTurn() {
        eTurns++;
        int dmg;
        String att;
        if (!finalBoss) { dmg = 1 + rnd.nextInt(2); att = "The Goblin Captain slashes you"; }
        else if (phase == 1) { dmg = 1 + rnd.nextInt(2); att = "The Dark Lord strikes"; }
        else if (phase == 2) { dmg = 2 + rnd.nextInt(2); att = "The enraged Dark Lord hits"; }
        else if (eTurns % 3 == 0) { dmg = 4; att = "DARK BLAST! The Dark Lord blasts"; }
        else { dmg = 2 + rnd.nextInt(2); att = "The Dark Lord hits"; }
        if (defending) { dmg = Math.max(1, dmg / 2); att += " (shield helps)"; }
        defending = false;
        p.hp -= dmg;
        shakePlayer = 12;
        Sound.play("hurt");
        log(att + " for " + dmg + "!");
        if (specialCd > 0) specialCd--;
        if (p.hp <= 0) {
            p.hp = 0;
            combatOver = true;
            playerDead = true;
            endTimer = 70;
        }
    }

    void updateCombat() {
        if (shakeEnemy > 0) shakeEnemy--;
        if (shakePlayer > 0) shakePlayer--;
        if (bannerTimer > 0) bannerTimer--;
        if (enemyDelay > 0 && --enemyDelay == 0) enemyTurn();
        if (combatOver && endTimer > 0 && --endTimer == 0) {
            if (playerDead) setState(State.GAME_OVER);
            else if (finalBoss) setState(State.VICTORY);
            else {
                p.attack++;                       // reward for finishing training
                setState(State.LEVEL3);
                flash("Training complete! Attack +1");
            }
        }
    }

    // ======================= LEVEL 3: SKYBOUND TRIALS (PLATFORMER) =======================
    void plat(double x, double y, double w) { plats.add(new Plat(x, y, w)); }

    void mplat(double x, double y, double w, double minX, double maxX, double speed) {
        Plat pl = new Plat(x, y, w);
        pl.minX = minX; pl.maxX = maxX; pl.speed = speed;
        plats.add(pl);
    }

    void initPlatformer() {
        plats.clear(); ladders.clear(); spikes.clear(); gems.clear();
        plat(0, 460, 300);
        plat(400, 460, 160);
        plat(640, 400, 150);
        plat(870, 340, 140);
        plat(1100, 460, 200);
        plat(1280, 260, 250);                   // reached by the ladder
        mplat(1550, 260, 90, 1550, 1800, 1.5);  // moving platform
        plat(1880, 300, 150);
        plat(2100, 400, 380);
        mplat(2540, 400, 100, 2540, 2780, 1.5); // moving platform
        plat(2860, 380, 340);
        ladders.add(new Rectangle(1290, 260, 24, 200));
        spikes.add(new Rectangle(1180, 440, 30, 20));
        spikes.add(new Rectangle(2250, 380, 60, 20));
        spikes.add(new Rectangle(2400, 380, 30, 20));
        double[][] gs = { {200, 420}, {480, 420}, {710, 360}, {940, 300}, {1180, 410},
                {1400, 220}, {1650, 215}, {1950, 260}, {2200, 350}, {2600, 360}, {3000, 340} };
        for (double[] g : gs) gems.add(new double[]{g[0], g[1], 0});
        cp = 0;
        respawn();
    }

    void respawn() {
        px = checkpoints[cp][0];
        py = checkpoints[cp][1] - PH;
        vy = 0; climbing = false; onGround = false; standing = null;
    }

    Rectangle ladderAt() {
        Rectangle box = new Rectangle((int) px, (int) py, PW, PH);
        for (Rectangle l : ladders) if (l.intersects(box)) return l;
        return null;
    }

    void updatePlatformer() {
        for (Plat pl : plats) {
            if (!pl.moving()) continue;
            pl.dx = pl.dir * pl.speed;
            pl.x += pl.dx;
            if (pl.x < pl.minX) { pl.x = pl.minX; pl.dir = 1; }
            if (pl.x + pl.w > pl.maxX) { pl.x = pl.maxX - pl.w; pl.dir = -1; }
        }
        double move = 0;
        if (left) move -= 4;
        if (right) move += 4;
        px = Math.max(0, Math.min(WORLD - PW, px + move));

        Rectangle lad = ladderAt();
        if (lad != null && (up || down)) climbing = true;
        if (climbing && lad == null) climbing = false;

        if (climbing) {
            vy = up ? -3 : (down ? 3 : 0);
            py += vy;
            onGround = false; standing = null;
            if (py + PH <= lad.y) { py = lad.y - PH; climbing = false; vy = 0; }
            else if (py + PH >= lad.y + lad.height) { py = lad.y + lad.height - PH; climbing = false; vy = 0; }
            if (space) { climbing = false; vy = -8; }
        } else {
            if ((space || up) && onGround) { vy = -11; onGround = false; standing = null; Sound.play("jump"); }
            if (standing != null && onGround) px += standing.dx;     // ride moving platforms
            double prevFeet = py + PH;
            vy = Math.min(vy + 0.6, 12);
            py += vy;
            onGround = false; standing = null;
            if (vy >= 0) {
                for (Plat pl : plats) {
                    if (px + PW > pl.x && px < pl.x + pl.w && prevFeet <= pl.y + 1 && py + PH >= pl.y) {
                        py = pl.y - PH; vy = 0; onGround = true; standing = pl;
                        break;
                    }
                }
            }
        }

        // hazards
        Rectangle box = new Rectangle((int) px + 3, (int) py + 4, PW - 6, PH - 4);
        for (Rectangle s : spikes) {
            if (s.intersects(box)) {
                p.hp = Math.max(1, p.hp - 1);
                Sound.play("hurt");
                flash("Ouch! Back to the checkpoint.");
                respawn();
                break;
            }
        }
        if (py > H + 80) { Sound.play("hurt"); flash("You fell! Back to the checkpoint."); respawn(); }

        // checkpoints
        for (int i = cp + 1; i < checkpoints.length; i++) {
            if (px >= checkpoints[i][0]) { cp = i; Sound.play("checkpoint"); flash("Checkpoint reached!"); }
        }
        // gems
        for (double[] g : gems) {
            if (g[2] == 0 && Math.abs(px + PW / 2.0 - g[0]) < 20 && Math.abs(py + PH / 2.0 - g[1]) < 24) {
                g[2] = 1; p.treasures++; Sound.play("gem"); flash("Gem collected!");
            }
        }
        camX = Math.max(0, Math.min(WORLD - W, px - 300));

        if (px >= 3100) {                         // end of the trial
            p.hp = Math.min(p.maxHp, p.hp + 3);   // rest before the final battle
            setState(State.DIALOGUE4);
        }
    }

    // ======================= GAME LOOP =======================
    @Override
    public void actionPerformed(ActionEvent e) {
        tick++;
        if (messageTimer > 0) messageTimer--;
        if (!paused) {
            switch (state) {
                case LEVEL1: updateLevel1(); break;
                case LEVEL3: updatePlatformer(); break;
                case LEVEL2: case LEVEL4: updateCombat(); break;
                default: break;
            }
        }
        repaint();
    }

    // ======================= RENDERING =======================
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        switch (state) {
            case MENU: drawMenu(g); break;
            case CREATION: drawCreation(g); break;
            case INTRO: drawIntro(g); break;
            case LEVEL1: drawLevel1(g); break;
            case SHOP: drawShop(g); break;
            case LEVEL2: case LEVEL4: drawCombat(g); break;
            case LEVEL3: drawPlatformer(g); break;
            case DIALOGUE4: drawDialogue4(g); break;
            case VICTORY: drawVictory(g); break;
            case GAME_OVER: drawGameOver(g); break;
        }
        for (Button b : buttons) drawButton(g, b);

        if (messageTimer > 0 && (state == State.LEVEL1 || state == State.LEVEL3)) {
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRoundRect(150, 66, 500, 34, 12, 12);
            text(g, message, W / 2, 89, 16, Color.WHITE);
        }
        if (paused) {
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(0, 0, W, H);
            text(g, "PAUSED", W / 2, 270, 48, Color.WHITE);
            text(g, "ESC - resume     M - main menu", W / 2, 320, 20, Color.LIGHT_GRAY);
        }
    }

    void text(Graphics2D g, String s, int cx, int y, int size, Color c) {
        g.setFont(new Font("SansSerif", Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(c);
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }

    void drawButton(Graphics2D g, Button b) {
        boolean en = b.enabled.getAsBoolean();
        boolean hover = en && b.r.contains(mouseX, mouseY);
        boolean sel = b.selected.getAsBoolean();
        Color fill = !en ? new Color(85, 85, 90)
                : sel ? new Color(235, 185, 45)
                : hover ? new Color(125, 95, 205) : new Color(80, 60, 150);
        g.setColor(fill);
        g.fillRoundRect(b.r.x, b.r.y, b.r.width, b.r.height, 14, 14);
        g.setColor(en ? new Color(255, 230, 150) : Color.GRAY);
        g.drawRoundRect(b.r.x, b.r.y, b.r.width, b.r.height, 14, 14);
        text(g, b.text.get(), b.r.x + b.r.width / 2, b.r.y + b.r.height / 2 + 6, 16,
                sel ? Color.BLACK : (en ? Color.WHITE : new Color(170, 170, 170)));
    }

    void bar(Graphics2D g, int x, int y, int w, int h, int v, int max, Color c) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(x - 2, y - 2, w + 4, h + 4);
        g.setColor(new Color(70, 20, 20));
        g.fillRect(x, y, w, h);
        g.setColor(c);
        g.fillRect(x, y, (int) (w * (double) Math.max(0, v) / max), h);
        g.setColor(Color.WHITE);
        g.drawRect(x, y, w, h);
    }

    /** Draws the customisable heroine. Box is roughly 24 x 34 before scaling. */
    void drawKnight(Graphics2D g, int x, int y, double s, boolean sword) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.scale(s, s);
        Color a = OUTFIT_COLORS[p.armor];
        Color hair = HAIR_COLORS[p.hair];
        Color skin = new Color(240, 200, 160);
        g.setColor(hair);                                   // long hair (behind her)
        g.fillRoundRect(3, 1, 18, 25, 10, 10);
        g.setColor(new Color(60, 40, 30));                  // boots
        g.fillRect(7, 30, 4, 4);
        g.fillRect(13, 30, 4, 4);
        g.setColor(skin);                                   // legs and arms
        g.fillRect(8, 27, 3, 4);
        g.fillRect(13, 27, 3, 4);
        g.fillRect(1, 14, 3, 9);
        g.fillRect(20, 14, 3, 9);
        g.setColor(a);                                      // skirt
        g.fillPolygon(new int[]{5, 19, 23, 1}, new int[]{20, 20, 28, 28}, 4);
        g.setColor(a.darker());
        g.fillRect(1, 27, 22, 1);
        g.setColor(a);                                      // bodice
        g.fillRoundRect(5, 12, 14, 9, 4, 4);
        g.setColor(a.brighter());                           // belt and shoulder pads
        g.fillRect(5, 19, 14, 2);
        g.fillRect(3, 12, 4, 3);
        g.fillRect(17, 12, 4, 3);
        g.setColor(skin);                                   // face
        g.fillOval(7, 2, 10, 11);
        g.setColor(hair);                                   // fringe
        g.fillArc(6, 0, 12, 9, 0, 180);
        g.setColor(Color.BLACK);                            // eyes
        g.fillRect(9, 7, 2, 2);
        g.fillRect(13, 7, 2, 2);
        g.setColor(new Color(200, 70, 90));                 // lips
        g.fillRect(11, 10, 2, 1);
        g.setColor(a.brighter());                           // hair bow
        g.fillOval(15, 1, 5, 4);
        if (sword) {
            g.setColor(new Color(225, 235, 245));
            g.fillRect(25, 2, 3, 22);
            g.setColor(new Color(150, 110, 40));
            g.fillRect(22, 24, 9, 3);
            g.fillRect(25, 27, 3, 5);
        }
        g.setTransform(old);
    }

    // ---------- Menu / creation / story screens ----------
    void drawMenu(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(15, 10, 40), 0, H, new Color(70, 25, 80)));
        g.fillRect(0, 0, W, H);
        g.setColor(Color.WHITE);
        for (int i = 0; i < 60; i++) {
            int sx = (i * 137) % W, sy = (i * 71) % 300;
            if ((tick / 20 + i) % 5 != 0) g.fillRect(sx, sy, 2, 2);
        }
        g.setColor(new Color(10, 5, 25));                       // castle silhouette
        g.fillRect(40, 440, 720, 160);
        for (int i = 0; i < 6; i++) { g.fillRect(60 + i * 120, 400, 60, 60); g.fillRect(70 + i * 120, 380, 10, 20); g.fillRect(100 + i * 120, 380, 10, 20); }
        text(g, "KNIGHT'S", W / 2, 120, 64, new Color(255, 215, 90));
        text(g, "FINAL QUEST", W / 2, 190, 64, new Color(255, 215, 90));
        text(g, "A Java fantasy adventure", W / 2, 230, 18, Color.LIGHT_GRAY);
        text(g, "Press N to mute / unmute sound", W / 2, 570, 14, Color.GRAY);
    }

    void drawCreation(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(40, 30, 80), 0, H, new Color(20, 15, 45)));
        g.fillRect(0, 0, W, H);
        text(g, "CHARACTER CREATION", W / 2, 60, 36, new Color(255, 215, 90));
        String shown = p.name + ((tick / 30) % 2 == 0 ? "_" : " ");
        text(g, "Name (type it):  " + shown, 330, 130, 22, Color.WHITE);
        g.setColor(new Color(255, 255, 255, 60));
        g.drawLine(60, 145, 560, 145);
        text(g, "Outfit", 90, 215, 20, Color.WHITE);
        text(g, "Hair colour", 115, 315, 20, Color.WHITE);
        g.setColor(new Color(255, 255, 255, 30));
        g.fillRoundRect(570, 150, 190, 270, 20, 20);
        drawKnight(g, 595, 190, 6.0, true);
        text(g, p.name.isEmpty() ? "Knight" : p.name, 665, 410, 18, Color.WHITE);
    }

    void drawIntro(Graphics2D g) {
        g.setColor(new Color(15, 10, 30));
        g.fillRect(0, 0, W, H);
        String n = p.name.isEmpty() ? "Knight" : p.name;
        String[] lines = {
            "Your journey begins...", "",
            "The kingdom of Eldoria has fallen to the Dark Lord.",
            "You, " + n + ", are its last knight.", "",
            "First, you must find the Legendary Sword",
            "hidden in the Lost Ruins - and escape alive.", "",
            "(Click or press SPACE to continue)" };
        for (int i = 0; i < lines.length; i++)
            text(g, lines[i], W / 2, 140 + i * 36, i == 0 ? 34 : 20, i == 0 ? new Color(255, 215, 90) : Color.WHITE);
        drawKnight(g, 388, 470, 3.0, false);
    }

    void drawDialogue4(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(25, 0, 40), 0, H, new Color(100, 10, 40)));
        g.fillRect(0, 0, W, H);
        drawDarkLord(g, 330, 60);
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(80, 400, 640, 130, 16, 16);
        String[] who = { "Dark Lord", "Knight" };
        String[] say = { "\"You have crossed the ruins and survived the trials.\"",
                         "\"And now I have come for you.\"" };
        int i = Math.min(dlgIdx, 1);
        text(g, who[i], W / 2, 440, 22, i == 0 ? new Color(220, 60, 90) : new Color(255, 215, 90));
        text(g, say[i], W / 2, 485, 20, Color.WHITE);
        text(g, "(click or SPACE)", W / 2, 520, 14, Color.GRAY);
    }

    void advanceDialogue() {
        Sound.play("click");
        if (state == State.INTRO) { setState(State.LEVEL1); return; }
        if (state == State.DIALOGUE4) {
            dlgIdx++;
            if (dlgIdx > 1) setState(State.LEVEL4);
        }
    }

    // ---------- Level 1 drawing ----------
    void drawLevel1(Graphics2D g) {
        g.setColor(new Color(20, 15, 35));
        g.fillRect(0, 0, W, H);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = c * TS, y = r * TS;
                char ch = map[r][c];
                if (ch == '#') {
                    g.setColor(new Color(62, 46, 100));
                    g.fillRect(x, y, TS, TS);
                    g.setColor(new Color(90, 70, 135));
                    g.drawRect(x, y, TS - 1, TS - 1);
                    g.drawLine(x, y + 16, x + TS, y + 16);
                    continue;
                }
                g.setColor((r + c) % 2 == 0 ? new Color(122, 106, 150) : new Color(112, 98, 142));
                g.fillRect(x, y, TS, TS);
                switch (ch) {
                    case 'C':
                        g.setColor(new Color(255, 200, 30)); g.fillOval(x + 9, y + 9, 14, 14);
                        g.setColor(new Color(255, 240, 150)); g.fillOval(x + 12, y + 12, 6, 6); break;
                    case 'S':
                        g.setColor(new Color(255, 240, 120, 110)); g.fillOval(x + 2, y + 2, 28, 28);
                        g.setColor(new Color(225, 235, 245)); g.fillRect(x + 14, y + 3, 4, 19);
                        g.setColor(new Color(150, 110, 40)); g.fillRect(x + 9, y + 21, 14, 3); g.fillRect(x + 14, y + 24, 4, 6); break;
                    case 'P':
                        g.setColor(new Color(220, 40, 60)); g.fillRoundRect(x + 9, y + 12, 14, 15, 6, 6);
                        g.setColor(new Color(230, 230, 240)); g.fillRect(x + 13, y + 6, 6, 7); break;
                    case 'T':
                        g.setColor(new Color(60, 220, 240));
                        g.fillPolygon(new int[]{x + 16, x + 25, x + 16, x + 7}, new int[]{y + 6, y + 16, y + 27, y + 16}, 4);
                        g.setColor(Color.WHITE); g.drawLine(x + 16, y + 6, x + 16, y + 27); break;
                    case 'K':
                        g.setColor(new Color(255, 200, 30)); g.drawOval(x + 8, y + 6, 10, 10);
                        g.fillRect(x + 12, y + 16, 3, 12); g.fillRect(x + 12, y + 22, 8, 3); break;
                    case 'D':
                        g.setColor(new Color(120, 75, 35)); g.fillRect(x + 2, y + 2, 28, 28);
                        g.setColor(new Color(255, 200, 30)); g.fillOval(x + 20, y + 14, 5, 5); break;
                    case 'E':
                        g.setColor(new Color(40, 200, 90, (int) (160 + 60 * Math.sin(tick / 10.0))));
                        g.fillRect(x + 2, y + 2, 28, 28);
                        text(g, "EXIT", x + 16, y + 21, 10, Color.WHITE); break;
                    case '^':
                        g.setColor(new Color(200, 200, 210));
                        for (int k = 0; k < 3; k++)
                            g.fillPolygon(new int[]{x + 2 + k * 10, x + 7 + k * 10, x + 12 + k * 10},
                                          new int[]{y + 28, y + 10, y + 28}, 3);
                        break;
                    default: break;
                }
            }
        }
        if (invuln % 10 < 5) drawKnight(g, (int) px - 1, (int) py - 8, 0.8, p.hasSword);
        drawHud(g, ROWS * TS, H - ROWS * TS);
    }

    void drawHud(Graphics2D g, int y, int h) {
        g.setColor(new Color(20, 15, 35, 235));
        g.fillRect(0, y, W, h);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(Color.WHITE);
        g.drawString(p.name.isEmpty() ? "Knight" : p.name, 14, y + 20);
        bar(g, 14, y + 28, 150, 14, p.hp, p.maxHp, new Color(220, 50, 60));
        g.setColor(Color.WHITE);
        g.drawString("HP " + p.hp + "/" + p.maxHp, 175, y + 40);
        g.drawString("Coins: " + p.coins, 270, y + 20);
        g.drawString("Potions: " + p.potions, 270, y + 42);
        g.drawString("Gems: " + p.treasures, 390, y + 20);
        g.drawString("Attack: " + p.attack, 390, y + 42);
        g.drawString("Sword: " + (p.hasSword ? "YES" : "no"), 500, y + 20);
        g.drawString("Key: " + (p.hasKey ? "YES" : "no"), 500, y + 42);
        g.drawString(state == State.LEVEL1 ? "Level 1 - Lost Ruins" : "Level 3 - Skybound Trials", 610, y + 30);
    }

    // ---------- Shop drawing ----------
    void drawShop(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(110, 70, 40), 0, H, new Color(70, 40, 25)));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(60, 35, 20));
        g.fillRect(0, 100, W, 12);
        for (int i = 0; i < 10; i++) {                       // bottles on the shelf
            g.setColor(new Color(60 + i * 18, 80, 220 - i * 15));
            g.fillRoundRect(40 + i * 75, 62, 24, 38, 8, 8);
            g.setColor(new Color(230, 230, 230));
            g.fillRect(48 + i * 75, 52, 8, 12);
        }
        text(g, "POTION SHOP", W / 2, 150, 34, new Color(255, 225, 140));
        text(g, "Coins: " + p.coins + "      HP: " + p.hp + "/" + p.maxHp, W / 2, 180, 20, Color.WHITE);
        text(g, shopMsg, W / 2, 440, 18, new Color(255, 245, 200));
    }

    // ---------- Combat drawing ----------
    void drawCombat(Graphics2D g) {
        boolean dark = finalBoss;
        if (!dark) {
            g.setPaint(new GradientPaint(0, 0, new Color(135, 206, 250), 0, 380, new Color(225, 242, 255)));
            g.fillRect(0, 0, W, 380);
            g.setColor(new Color(215, 185, 125));
            g.fillRect(0, 380, W, 220);
            g.setColor(new Color(170, 140, 90));
            g.fillRect(0, 380, W, 6);
        } else {
            g.setPaint(new GradientPaint(0, 0, new Color(30, 0, 50), 0, 380, new Color(120, 10, 45)));
            g.fillRect(0, 0, W, 380);
            g.setColor(new Color(20, 10, 35));
            for (int i = 0; i < 4; i++) g.fillRect(40 + i * 230, 60, 40, 320);
            g.setColor(new Color(40, 22, 55));
            g.fillRect(0, 380, W, 220);
        }
        int ex = 540 + (shakeEnemy > 0 ? (shakeEnemy % 2 == 0 ? 6 : -6) : 0);
        if (dark) drawDarkLord(g, ex - 60, 80); else drawGoblin(g, ex, 150);
        int kx = 120 + (shakePlayer > 0 ? (shakePlayer % 2 == 0 ? -6 : 6) : 0);
        drawKnight(g, kx, 235, 4.0, p.hasSword);

        // Enemy HP
        text(g, eName + (finalBoss ? "  (Phase " + phase + ")" : ""), W / 2, 28, 20, Color.WHITE);
        bar(g, 240, 38, 320, 20, eHp, eMax, new Color(200, 40, 200));
        text(g, eHp + " HP", W / 2, 54, 14, Color.WHITE);

        // Player info
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(25, 385, 290, 115, 12, 12);
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(Color.WHITE);
        g.drawString((p.name.isEmpty() ? "Knight" : p.name) + " - " + p.hp + "/" + p.maxHp + " HP", 40, 408);
        bar(g, 40, 416, 255, 16, p.hp, p.maxHp, new Color(220, 50, 60));
        g.setColor(Color.WHITE);
        g.drawString("Coins: " + p.coins + "     Potions: " + p.potions, 40, 456);
        g.drawString("Attack: " + p.attack + (defending ? "     [DEFENDING]" : ""), 40, 480);

        // Combat log
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(330, 385, 445, 115, 12, 12);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(Color.WHITE);
        for (int i = 0; i < logLines.size(); i++) g.drawString(logLines.get(i), 345, 408 + i * 20);

        if (bannerTimer > 0) {
            text(g, banner, W / 2 + 3, 233, 44, Color.BLACK);
            text(g, banner, W / 2, 230, 44, new Color(255, 60, 60));
        }
        if (combatOver) {
            String t = playerDead ? "YOU FALL..." : "VICTORY!";
            text(g, t, W / 2, 300, 54, playerDead ? new Color(255, 70, 70) : new Color(255, 215, 90));
        }
    }

    void drawGoblin(Graphics2D g, int x, int y) {
        g.setColor(new Color(100, 70, 40));                         // club
        g.fillRoundRect(x + 100, y + 30, 18, 130, 8, 8);
        g.setColor(new Color(60, 140, 60));
        g.fillOval(x, y + 60, 100, 120);                            // body
        g.fillOval(x + 15, y, 70, 70);                              // head
        g.fillPolygon(new int[]{x + 18, x - 20, x + 20}, new int[]{y + 20, y + 30, y + 50}, 3);   // ears
        g.fillPolygon(new int[]{x + 82, x + 120, x + 80}, new int[]{y + 20, y + 30, y + 50}, 3);
        g.setColor(new Color(150, 150, 160));                       // helmet
        g.fillArc(x + 12, y - 4, 76, 44, 0, 180);
        g.setColor(new Color(255, 230, 60));
        g.fillOval(x + 30, y + 30, 10, 10);
        g.fillOval(x + 60, y + 30, 10, 10);
        g.setColor(Color.BLACK);
        g.fillOval(x + 33, y + 33, 4, 4);
        g.fillOval(x + 63, y + 33, 4, 4);
        g.drawArc(x + 35, y + 45, 30, 14, 180, 180);
    }

    void drawDarkLord(Graphics2D g, int x, int y) {
        int cx = x + 60;
        if (phase >= 2 && state != State.DIALOGUE4) {
            int a = (int) (70 + 40 * Math.sin(tick / 6.0));
            g.setColor(new Color(255, 30, 30, a));
            g.fillOval(cx - 110, y - 30, 220, 300);
        }
        g.setColor(new Color(60, 15, 90));                          // cloak
        g.fillPolygon(new int[]{cx - 35, cx + 35, cx + 85, cx - 85},
                      new int[]{y + 55, y + 55, y + 260, y + 260}, 4);
        g.setColor(new Color(35, 8, 55));
        g.fillPolygon(new int[]{cx - 15, cx + 15, cx + 30, cx - 30},
                      new int[]{y + 55, y + 55, y + 260, y + 260}, 4);
        g.setColor(new Color(60, 55, 75));                          // head
        g.fillOval(cx - 30, y, 60, 62);
        g.setColor(new Color(30, 25, 40));                          // horns
        g.fillPolygon(new int[]{cx - 26, cx - 52, cx - 12}, new int[]{y + 14, y - 38, y + 4}, 3);
        g.fillPolygon(new int[]{cx + 26, cx + 52, cx + 12}, new int[]{y + 14, y - 38, y + 4}, 3);
        g.setColor(new Color(255, 200, 40));                        // crown
        g.fillRect(cx - 22, y - 6, 44, 10);
        for (int i = 0; i < 4; i++) g.fillPolygon(new int[]{cx - 22 + i * 12, cx - 16 + i * 12, cx - 10 + i * 12},
                                                  new int[]{y - 6, y - 20, y - 6}, 3);
        g.setColor(new Color(255, 40, 40));                         // eyes
        g.fillOval(cx - 18, y + 24, 12, 8);
        g.fillOval(cx + 6, y + 24, 12, 8);
        g.setColor(new Color(40, 40, 55));                          // dark sword
        g.fillRect(cx + 78, y + 70, 8, 150);
        g.setColor(new Color(150, 20, 50));
        g.fillRect(cx + 66, y + 190, 32, 6);
    }

    // ---------- Level 3 drawing ----------
    void drawPlatformer(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(110, 185, 245), 0, H, new Color(255, 240, 220)));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(255, 255, 255, 200));                  // parallax clouds
        for (int i = 0; i < 14; i++) {
            double cxp = (i * 260 - camX * 0.5) % (W + 300);
            if (cxp < -150) cxp += W + 300;
            int cy = 120 + (i * 53) % 200;
            g.fillOval((int) cxp, cy, 110, 36);
            g.fillOval((int) cxp + 25, cy - 18, 70, 40);
        }
        AffineTransform old = g.getTransform();
        g.translate(-camX, 0);

        for (Plat pl : plats) {
            int x = (int) pl.x, y = (int) pl.y, w = (int) pl.w;
            g.setColor(pl.moving() ? new Color(90, 130, 190) : new Color(130, 95, 60));
            g.fillRoundRect(x, y + 10, w, 28, 8, 8);
            g.setColor(pl.moving() ? new Color(140, 190, 240) : new Color(80, 170, 70));
            g.fillRect(x, y, w, 12);
        }
        for (Rectangle l : ladders) {
            g.setColor(new Color(140, 95, 45));
            g.fillRect(l.x, l.y, 4, l.height);
            g.fillRect(l.x + l.width - 4, l.y, 4, l.height);
            for (int yy = l.y + 8; yy < l.y + l.height; yy += 16) g.fillRect(l.x, yy, l.width, 3);
        }
        g.setColor(new Color(200, 200, 215));
        for (Rectangle s : spikes)
            for (int xx = s.x; xx < s.x + s.width; xx += 10)
                g.fillPolygon(new int[]{xx, xx + 5, xx + 10}, new int[]{s.y + s.height, s.y, s.y + s.height}, 3);
        for (int i = 0; i < checkpoints.length; i++) {              // checkpoint flags
            int fx = checkpoints[i][0], fy = checkpoints[i][1];
            g.setColor(new Color(90, 90, 90)); g.fillRect(fx, fy - 50, 3, 50);
            g.setColor(i <= cp ? new Color(60, 200, 90) : new Color(160, 160, 160));
            g.fillPolygon(new int[]{fx + 3, fx + 28, fx + 3}, new int[]{fy - 50, fy - 40, fy - 30}, 3);
        }
        for (double[] gm : gems) {
            if (gm[2] == 1) continue;
            int gx = (int) gm[0], gy = (int) gm[1] + (int) (3 * Math.sin(tick / 8.0 + gx));
            g.setColor(new Color(60, 220, 240));
            g.fillPolygon(new int[]{gx, gx + 9, gx, gx - 9}, new int[]{gy - 10, gy, gy + 10, gy}, 4);
        }
        g.setColor(new Color(90, 90, 90)); g.fillRect(3100, 280, 4, 100);   // goal flag
        g.setColor(new Color(255, 205, 40));
        g.fillPolygon(new int[]{3104, 3150, 3104}, new int[]{280, 300, 320}, 3);
        text(g, "GOAL", 3105, 270, 16, new Color(120, 80, 0));

        drawKnight(g, (int) px, (int) py - 1, 0.94, p.hasSword);
        g.setTransform(old);
        drawHud(g, 0, 56);
    }

    // ---------- End screens ----------
    void drawVictory(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(255, 215, 90), 0, H, new Color(255, 120, 60)));
        g.fillRect(0, 0, W, H);
        text(g, "VICTORY", W / 2, 110, 64, new Color(60, 20, 0));
        text(g, "DARK LORD DEFEATED", W / 2, 170, 28, new Color(60, 20, 0));
        text(g, "The kingdom is finally safe.", W / 2, 215, 22, new Color(80, 30, 0));
        text(g, "Congratulations, " + (p.name.isEmpty() ? "Knight" : p.name) + "!", W / 2, 255, 28, Color.WHITE);
        text(g, "Gems collected: " + p.treasures + "    Coins left: " + p.coins, W / 2, 300, 18, new Color(80, 30, 0));
        drawKnight(g, 340, 320, 4.0, true);
    }

    void drawGameOver(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(10, 0, 0), 0, H, new Color(90, 10, 20)));
        g.fillRect(0, 0, W, H);
        text(g, "YOU HAVE FALLEN", W / 2, 150, 54, new Color(230, 60, 60));
        text(g, "Your journey ends here.", W / 2, 210, 24, Color.LIGHT_GRAY);
        text(g, "Retry restarts this battle with your earlier health and items.", W / 2, 260, 16, Color.GRAY);
    }

    // ======================= INPUT =======================
    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) left = true;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = true;
        if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) up = true;
        if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) down = true;
        if (k == KeyEvent.VK_SPACE) {
            space = true;
            if (state == State.INTRO || state == State.DIALOGUE4) advanceDialogue();
        }
        if (k == KeyEvent.VK_ENTER && (state == State.INTRO || state == State.DIALOGUE4)) advanceDialogue();
        if (k == KeyEvent.VK_BACK_SPACE && state == State.CREATION && !p.name.isEmpty())
            p.name = p.name.substring(0, p.name.length() - 1);
        if (k == KeyEvent.VK_ESCAPE && (state == State.LEVEL1 || state == State.LEVEL3)) paused = !paused;
        if (k == KeyEvent.VK_M && paused) setState(State.MENU);
        if (k == KeyEvent.VK_N) { Sound.toggleMute(); flash(Sound.muted ? "Sound OFF" : "Sound ON"); }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) left = false;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = false;
        if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) up = false;
        if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) down = false;
        if (k == KeyEvent.VK_SPACE) space = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (state == State.CREATION) {
            char ch = e.getKeyChar();
            if (ch >= 32 && ch < 127 && p.name.length() < 12) p.name += ch;
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        for (Button b : new ArrayList<>(buttons)) {
            if (b.r.contains(e.getPoint()) && b.enabled.getAsBoolean()) { Sound.play("click"); b.action.run(); return; }
        }
        if (state == State.INTRO || state == State.DIALOGUE4) advanceDialogue();
    }

    @Override public void mouseMoved(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mouseDragged(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseReleased(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
}