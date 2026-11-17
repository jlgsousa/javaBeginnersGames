package daemmerwald;

import java.util.Random;

/** Kachelkarte des Dämmerwaldes: halb handplatziert, halb prozedural. */
public class WorldMap {
    public static final int TS = 32, W = 90, H = 70;

    public static final int GRASS = 0, PATH = 1, TREE = 2, WATER = 3, STONE = 4, SWAMP = 5, FLOOR = 6, WALL = 7,
        BLIGHT = 8, FLOWER = 9, MUSH = 10, ROCK = 11, SHELF = 12, HEART = 13, CARPET = 14, ALTAR = 15, CRYSTAL = 16;

    // Orte (Kachelkoordinaten)
    public static final int[] COURTYARD = {45, 19}, HEART_C = {45, 38}, LAKE = {14, 34}, QUARRY = {75, 26},
        SWAMP_C = {45, 58}, MUSHROOMS = {15, 58}, RAVINE = {77, 56},
        SHADOW = {14, 12}, MEADOW = {74, 9};

    public final int[][] t = new int[W][H];
    public final float[][] var = new float[W][H];
    private final boolean[][] pathMask = new boolean[W][H];
    private final Random rnd;

    public WorldMap(long seed) {
        rnd = new Random(seed);
        generate();
    }

    public int get(int x, int y) {
        if (x < 0 || y < 0 || x >= W || y >= H) return TREE;
        return t[x][y];
    }

    public static boolean solid(int tile) {
        return tile == TREE || tile == WATER || tile == WALL || tile == BLIGHT || tile == ROCK || tile == SHELF || tile == HEART
            || tile == ALTAR || tile == CRYSTAL;
    }

    public boolean solidAtPixel(double px, double py) {
        if (px < 0 || py < 0) return true;
        return solid(get((int) (px / TS), (int) (py / TS)));
    }

    private static double dist(int x, int y, int[] c) {
        return Math.hypot(x - c[0], y - c[1]);
    }

    private void generate() {
        // 1) Wald aus Wertrauschen
        int gw = W / 4 + 2, gh = H / 4 + 2;
        double[][] grid = new double[gw][gh];
        for (int i = 0; i < gw; i++) for (int j = 0; j < gh; j++) grid[i][j] = rnd.nextDouble();
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                double fx = x / 4.0, fy = y / 4.0;
                int ix = (int) fx, iy = (int) fy;
                double ax = fx - ix, ay = fy - iy;
                ax = ax * ax * (3 - 2 * ax);
                ay = ay * ay * (3 - 2 * ay);
                double n = grid[ix][iy] * (1 - ax) * (1 - ay) + grid[ix + 1][iy] * ax * (1 - ay)
                    + grid[ix][iy + 1] * (1 - ax) * ay + grid[ix + 1][iy + 1] * ax * ay;
                n += rnd.nextDouble() * 0.3;
                t[x][y] = n > 0.55 ? TREE : GRASS;
                var[x][y] = rnd.nextFloat();
            }
        }

        // 2) Lichtungen
        clearing(COURTYARD, 6);
        clearing(HEART_C, 8);
        clearing(LAKE, 11);
        clearing(QUARRY, 9);
        clearing(SWAMP_C, 9);
        clearing(MUSHROOMS, 6);
        clearing(SHADOW, 7);
        clearing(MEADOW, 8);

        // 3) Pfade
        path(45, 15, 45, 31);
        path(45, 45, 45, 51);
        path(38, 38, 24, 34);
        path(52, 37, 68, 28);
        path(20, 42, 16, 54);
        path(52, 58, 68, 56);
        path(50, 20, 68, 24);
        path(40, 19, 20, 14);
        path(50, 17, 68, 11);

        // 4) Regionen
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                double a;
                // Mondsee
                double dl = dist(x, y, LAKE);
                a = Math.atan2(y - LAKE[1], x - LAKE[0]);
                if (dl < 6.0 + Math.sin(a * 3) * 0.8 + Math.cos(a * 5) * 0.4) t[x][y] = WATER;
                // Steinbruch
                double dq = dist(x, y, QUARRY);
                if (dq < 8.5) {
                    t[x][y] = STONE;
                    if (!pathMask[x][y] && dq > 2.5 && rnd.nextDouble() < 0.09) t[x][y] = ROCK;
                }
                // Moor
                double ds = dist(x, y, SWAMP_C);
                if (ds < 8.5) {
                    t[x][y] = SWAMP;
                    if (!pathMask[x][y] && ds > 2.5 && rnd.nextDouble() < 0.07) t[x][y] = WATER;
                }
                // Pilzkreis
                double dm = dist(x, y, MUSHROOMS);
                if (dm >= 3.0 && dm < 3.9) t[x][y] = MUSH;
                else if (dm < 3.0) t[x][y] = GRASS;
            }
        }

        // Dornenschlucht: Felsring, Eingang im Westen durch die Welke versperrt
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                double d = dist(x, y, RAVINE);
                double ang = Math.atan2(y - RAVINE[1], x - RAVINE[0]);
                double west = Math.abs(Math.abs(ang) - Math.PI);
                if (d < 7) {
                    t[x][y] = var[x][y] < 0.4 ? STONE : GRASS;
                    if (d > 3 && west > 0.5 && rnd.nextDouble() < 0.06) t[x][y] = ROCK;
                } else if (d < 9.6) {
                    t[x][y] = west < 0.34 ? BLIGHT : ROCK;
                }
            }
        }

        // Akademie
        for (int x = 36; x <= 54; x++) {
            for (int y = 3; y <= 14; y++) {
                boolean border = x == 36 || x == 54 || y == 3 || y == 14;
                t[x][y] = border ? WALL : FLOOR;
            }
        }
        for (int x = 44; x <= 46; x++) {
            t[x][14] = FLOOR;
            t[x][15] = PATH;
            for (int y = 4; y <= 13; y++) t[x][y] = CARPET;
        }
        for (int x = 37; x <= 42; x++) t[x][4] = SHELF;
        for (int y = 9; y <= 11; y++) { t[37][y] = SHELF; t[41][y] = SHELF; }
        for (int x = 48; x <= 53; x += 5) t[x][4] = SHELF;

        // Schattenhain: Ring aus toten Bäumen, dunkler Altar
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                double d = dist(x, y, SHADOW);
                if (d < 12 && t[x][y] == TREE && rnd.nextDouble() < 0.75) t[x][y] = BLIGHT;
                if (d >= 5 && d < 7 && t[x][y] == GRASS && !pathMask[x][y] && rnd.nextDouble() < 0.12) t[x][y] = BLIGHT;
            }
        }
        t[SHADOW[0]][SHADOW[1]] = ALTAR;
        // Lichtwiese: Blumenmeer und Kristall
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (dist(x, y, MEADOW) < 7.5 && (t[x][y] == GRASS) && rnd.nextDouble() < 0.4) t[x][y] = FLOWER;
            }
        }
        t[MEADOW[0]][MEADOW[1]] = CRYSTAL;

        // Herzbaum
        for (int x = 44; x <= 46; x++) for (int y = 37; y <= 39; y++) t[x][y] = HEART;

        // Abgestorbene Bäume um den Herzbaum und an den Rändern der Welke
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (t[x][y] != TREE) continue;
                double dh = dist(x, y, HEART_C);
                if (dh > 6 && dh < 14 && rnd.nextDouble() < 0.4) t[x][y] = BLIGHT;
                if (dist(x, y, SWAMP_C) < 12 && rnd.nextDouble() < 0.15) t[x][y] = BLIGHT;
                if (dist(x, y, LAKE) < 13 && rnd.nextDouble() < 0.12) t[x][y] = BLIGHT;
            }
        }

        // Deko
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (t[x][y] != GRASS) continue;
                double r = rnd.nextDouble();
                if (r < 0.05) t[x][y] = FLOWER;
                else if (r < 0.075) t[x][y] = MUSH;
            }
        }

        // Rand
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++)
            if (x < 2 || y < 2 || x >= W - 2 || y >= H - 2) t[x][y] = TREE;
    }

    private void clearing(int[] c, double r) {
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                double a = Math.atan2(y - c[1], x - c[0]);
                if (dist(x, y, c) < r + Math.sin(a * 3 + c[0]) * 0.8 && t[x][y] == TREE) t[x][y] = GRASS;
            }
        }
    }

    private void path(int ax, int ay, int bx, int by) {
        double len = Math.hypot(bx - ax, by - ay);
        int steps = (int) (len * 4);
        double nx = -(by - ay) / len, ny = (bx - ax) / len;
        double phase = rnd.nextDouble() * 6;
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            double wob = Math.sin(f * Math.PI * 2.2 + phase) * 1.4 * Math.sin(f * Math.PI);
            double x = ax + (bx - ax) * f + nx * wob;
            double y = ay + (by - ay) * f + ny * wob;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    int tx = (int) Math.round(x) + dx, ty = (int) Math.round(y) + dy;
                    if (tx < 0 || ty < 0 || tx >= W || ty >= H) continue;
                    double d = Math.hypot(tx - x, ty - y);
                    if (d < 1.3) {
                        t[tx][ty] = PATH;
                        pathMask[tx][ty] = true;
                    } else if (d < 2.6 && t[tx][ty] == TREE) {
                        t[tx][ty] = GRASS;
                    }
                }
            }
        }
    }
}
