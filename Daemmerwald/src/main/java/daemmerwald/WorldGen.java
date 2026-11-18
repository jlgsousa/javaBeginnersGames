package daemmerwald;

/**
 * Die Welt als reine Funktion: Höhe, Untergrund und Bewuchs an jedem Punkt (x,z) in Metern.
 * Im Zentrum liegt die Story-Region (Akademie, Herzbaum, ...), im Süden das Meer mit der Café-Klippe,
 * nach allen anderen Seiten setzt sich die Landschaft endlos fort.
 * Thread-sicher (wird von Hintergrund-Threads beim Nachladen benutzt).
 */
public final class WorldGen {
    private WorldGen() { }

    public static final float WATER = 0f;

    // Story-Orte (x, z)
    public static final float[] ACADEMY = {0, -112}, COURTYARD = {0, -66}, HEART = {0, 12}, LAKE = {-124, -4},
        QUARRY = {120, -36}, SWAMP = {0, 92}, MUSHROOMS = {-120, 92}, RAVINE = {128, 84}, SHADOW = {-124, -92},
        MEADOW = {116, -104}, CAFE = {34, 194}, HOME = {-66, -52},
        VILLAGE = {-92, -40}, PALACE = {0, -200}, GROVE = {-150, -160};

    /** Der Laternbach: fließt durch das Feendorf Laternhain in den Mondsee. */
    public static final float[][] STREAM = {{-90, -84}, {-94, -62}, {-92, -44}, {-96, -28}, {-102, -15}, {-112, -6}};

    /** Pfade als Polylinien {x1,z1,x2,z2}. */
    public static final float[][] PATHS = {
        {0, -92, 0, -14}, {0, 30, 0, 66}, {-26, 14, -88, -2}, {26, 6, 92, -30}, {-100, 22, -114, 74},
        {26, 94, 100, 84}, {20, -62, 94, -44}, {-20, -66, -100, -86}, {20, -74, 92, -98}, {12, 104, 30, 182},
        {-16, -64, -58, -50}, {-58, -50, -84.3f, -37.8f}, {0, -132, 0, -170}, {-101.7f, -42.2f, -112, -60}, {-124, -92, -140, -134}, {-80, -62, -82, -89}
    };

    private static final Feature[] FLAT = {
        new Feature(0, -100, 54), new Feature(COURTYARD, 30), new Feature(HEART, 34), new Feature(MEADOW, 36),
        new Feature(MUSHROOMS, 22), new Feature(SHADOW, 28), new Feature(CAFE, 22), new Feature(LAKE, 46), new Feature(HOME, 28), new Feature(VILLAGE, 34), new Feature(PALACE, 52), new Feature(GROVE, 30), new Feature(0, -150, 22), new Feature(-91, -95, 36)
    };

    private record Feature(float x, float z, float r) {
        Feature(float[] p, float r) { this(p[0], p[1], r); }
    }

    static double dist(double x, double z, float[] c) { return Math.hypot(x - c[0], z - c[1]); }

    // ---------------------------------------------------------------- Höhe

    /** Grundrelief der Story-Region (ohne Eingriffe). */
    private static double coreBase(double x, double z) {
        return 3.2 + (G3.fbm(x * 0.012 + 11, z * 0.012 + 7, 4) - 0.5) * 10 + (G3.fbm(x * 0.05, z * 0.05, 3) - 0.5) * 1.6;
    }

    /** Endlose Landschaft außerhalb. */
    private static double wild(double x, double z) {
        double n1 = G3.fbm(x * 0.0042 + 31, z * 0.0042 - 17, 4);
        double n2 = G3.fbm(x * 0.021 + 100, z * 0.021, 3);
        double h = 5 + (n1 - 0.45) * 46 + (n2 - 0.5) * 6;
        double m = G3.fbm(x * 0.0016 + 50, z * 0.0016 - 30, 3);
        if (m > 0.58) h += Math.pow((m - 0.58) / 0.42, 1.4) * 150 * (0.8 + 0.4 * G3.fbm(x * 0.03, z * 0.03, 3));
        double l = G3.fbm(x * 0.0065 - 70, z * 0.0065 + 40, 3);
        if (l < 0.34) h = lerp(h, WATER - 5, G3.smooth(0.34f, 0.25f, (float) l));
        return h;
    }

    /** Südküste: 0 = Land, 1 = offenes Meer. */
    public static double sea(double x, double z) {
        double coast = 222 + Math.sin(x * 0.011) * 22 + (G3.fbm(x * 0.01 + 5, 3, 3) - 0.5) * 40;
        double width = 1400 + Math.sin(x * 0.002) * 200;
        double s = smooth(coast, coast + (Math.abs(x - 40) < 160 ? 7 : 26), z);
        s *= 1 - smooth(coast + width, coast + width + 120, z);
        s *= 1 - smooth(1100, 1300, Math.abs(x - 30));
        return s;
    }

    public static float height(double x, double z) {
        double r = Math.hypot(x, z * 1.15);
        double core = coreBase(x, z);
        double h = core;
        // Abflachen der Lichtungen
        for (Feature f : FLAT) {
            double d = Math.hypot(x - f.x, z - f.z);
            if (d < f.r) h = lerp(h, coreBase(f.x, f.z), G3.smooth(f.r, f.r * 0.5f, (float) d));
        }
        // Mondsee
        double dl = dist(x, z, LAKE) + (G3.fbm(x * 0.05, z * 0.05, 2) - 0.5) * 8;
        h = lerp(h, WATER - 5, G3.smooth(31, 18, (float) dl));
        // Moor mit Tümpeln
        double ds = dist(x, z, SWAMP);
        h = lerp(h, -0.3 + (G3.fbm(x * 0.13, z * 0.13, 2) - 0.5) * 1.7, G3.smooth(34, 18, (float) ds));
        // Steinbruch: Grube mit Felsrand, Öffnung nach Westen
        double dq = dist(x, z, QUARRY);
        if (dq < 40) {
            double ang = Math.atan2(z - QUARRY[1], x - QUARRY[0]);
            double gap = Math.abs(Math.abs(ang) - Math.PI);
            double rim = G3.smooth(20, 26, (float) dq) * (1 - G3.smooth(32, 40, (float) dq)) * (1 - G3.smooth(0.55f, 0.3f, (float) gap));
            h = lerp(h, coreBase(QUARRY[0], QUARRY[1]) - 1.5, G3.smooth(24, 14, (float) dq));
            h += rim * 11 * (0.7 + 0.6 * G3.fbm(x * 0.2, z * 0.2, 3));
        }
        // Dornenschlucht: Felskessel, Eingang im Westen
        double dr = dist(x, z, RAVINE);
        if (dr < 48) {
            double ang = Math.atan2(z - RAVINE[1], x - RAVINE[0]);
            double gap = Math.abs(Math.abs(ang) - Math.PI);
            double rim = G3.smooth(24, 29, (float) dr) * (1 - G3.smooth(40, 48, (float) dr)) * (1 - G3.smooth(0.42f, 0.22f, (float) gap));
            h = lerp(h, coreBase(RAVINE[0], RAVINE[1]), G3.smooth(26, 16, (float) dr));
            h += rim * 22 * (0.65 + 0.7 * G3.fbm(x * 0.15 + 4, z * 0.15, 3));
        }
        // Laternbach mit flachen Ufern
        double sd = streamDist(x, z);
        if (sd < 6.5) h = lerp(h, -1.9, G3.smooth(6.5f, 2.2f, (float) sd));
        // Klippen-Plateau beim Café
        double plateau = smooth(130, 196, z) * smooth(-170, -60, x) * (1 - smooth(170, 260, x));
        h += plateau * 20;
        // Schlosssee: hinter und neben dem Schloss, vorn bleibt der Weg trocken
        double dp = dist(x, z, PALACE);
        double causeway = z > PALACE[1] ? 1 - smooth(9, 14, Math.abs(x - PALACE[0])) : 0;
        double lake = G3.smooth(44, 50, (float) dp) * (1 - G3.smooth(72, 84, (float) dp)) * (1 - causeway);
        if (lake > 0) h = lerp(h, WATER - 3.5, lake);
        // Übergang in die endlose Wildnis
        double w = smooth(250, 360, r);
        if (w > 0) h = lerp(h, wild(x, z) + plateau * 20, w);
        // Berge im Norden hinter dem Schloss
        double north = smooth(PALACE[1] - 85, PALACE[1] - 150, z) * (1 - smooth(200, 320, Math.abs(x - PALACE[0])));
        if (north > 0) {
            double ridge = G3.fbm(x * 0.011 + 40, z * 0.011 - 15, 4);
            double peaks = Math.pow(Math.max(0, G3.fbm(x * 0.024 - 7, z * 0.024 + 3, 3) - 0.35) / 0.65, 1.6);
            h = Math.max(h, h * (1 - north) + north * (40 + ridge * 80 + peaks * 120));
        }
        // Meer
        double s = sea(x, z);
        if (s > 0) h = lerp(h, -16 - G3.fbm(x * 0.01, z * 0.01, 2) * 10, s);
        // Inseln im Meer
        double isl = G3.fbm(x * 0.008 + 300, z * 0.008 - 200, 3);
        if (s > 0.9 && isl > 0.68) h = Math.max(h, -16 + (isl - 0.68) / 0.32 * 70);
        // Festland nicht unter Wasser (außer Seen/Moor)
        if (r < 250 && dl > 34 && ds > 36 && sd > 5 && z < 200 && lake < 0.02) h = Math.max(h, 1.0);
        return (float) h;
    }

    // ---------------------------------------------------------------- Pfade & Zonen

    /** Abstand zum Laternbach (leicht geschlängelt). */
    public static double streamDist(double x, double z) {
        double best = 1e9;
        for (int i = 0; i + 1 < STREAM.length; i++) {
            float[] a = STREAM[i], b = STREAM[i + 1];
            double dx = b[0] - a[0], dz = b[1] - a[1];
            double t = Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / (dx * dx + dz * dz)));
            double[] p = streamPoint(i, t);
            best = Math.min(best, Math.hypot(x - p[0], z - p[1]));
        }
        return best;
    }

    /** Punkt auf dem Bachlauf: Abschnitt i, Anteil t (0..1). */
    public static double[] streamPoint(int i, double t) {
        float[] a = STREAM[i], b = STREAM[i + 1];
        double dx = b[0] - a[0], dz = b[1] - a[1];
        return new double[]{a[0] + dx * t + Math.sin(t * Math.PI * 2 + i) * 1.6 * Math.sin(t * Math.PI), a[1] + dz * t};
    }

    public static double pathDist(double x, double z) {
        double best = 1e9;
        for (float[] p : PATHS) {
            double dx = p[2] - p[0], dz = p[3] - p[1];
            double t = ((x - p[0]) * dx + (z - p[1]) * dz) / (dx * dx + dz * dz);
            t = Math.max(0, Math.min(1, t));
            double px = p[0] + dx * t + Math.sin(t * Math.PI * 3 + p[0]) * 2.5 * Math.sin(t * Math.PI);
            double pz = p[1] + dz * t;
            best = Math.min(best, Math.hypot(x - px, z - pz));
        }
        return best;
    }

    /** Dunkle Orte (1) – Welke, Schattenwälder. */
    public static float darkness(double x, double z) {
        float d = 0;
        d = Math.max(d, G3.smooth(40, 22, (float) dist(x, z, HEART)) * 0.8f);
        d = Math.max(d, G3.smooth(40, 24, (float) dist(x, z, SWAMP)));
        d = Math.max(d, G3.smooth(44, 26, (float) dist(x, z, RAVINE)));
        d = Math.max(d, G3.smooth(42, 22, (float) dist(x, z, SHADOW)));
        d = Math.max(d, G3.smooth(60, 30, (float) dist(x, z, GROVE)));
        if (Math.hypot(x, z) > 300) {
            double n = G3.fbm(x * 0.003 - 400, z * 0.003 + 90, 3);
            d = Math.max(d, G3.smooth(0.62f, 0.72f, (float) n));
        }
        return d;
    }

    /** Helle, fröhliche Orte (1). */
    public static float brightness(double x, double z) {
        float b = 0;
        b = Math.max(b, G3.smooth(48, 30, (float) dist(x, z, MEADOW)));
        b = Math.max(b, G3.smooth(46, 30, (float) dist(x, z, LAKE)));
        b = Math.max(b, G3.smooth(30, 16, (float) dist(x, z, MUSHROOMS)));
        b = Math.max(b, G3.smooth(36, 20, (float) dist(x, z, COURTYARD)));
        b = Math.max(b, G3.smooth(30, 16, (float) dist(x, z, CAFE)));
        b = Math.max(b, G3.smooth(40, 22, (float) dist(x, z, VILLAGE)));
        if (Math.hypot(x, z) > 300) {
            double n = G3.fbm(x * 0.003 + 120, z * 0.003 - 60, 3);
            b = Math.max(b, G3.smooth(0.64f, 0.74f, (float) n));
        }
        return b * (1 - darkness(x, z));
    }

    /** Baumdichte 0..1. */
    public static float forest(double x, double z, float h, float slope) {
        if (h < WATER + 0.6f || slope > 1.1f) return 0;
        double base = G3.fbm(x * 0.008 + 9, z * 0.008 - 3, 3);
        float f = G3.smooth(0.28f, 0.55f, (float) base);
        for (Feature c : FLAT) {
            double d = Math.hypot(x - c.x, z - c.z);
            if (d < c.r * 1.05) f *= G3.smooth((float) c.r * 0.7f, (float) c.r * 1.05f, (float) d);
        }
        f *= G3.smooth(26, 34, (float) dist(x, z, QUARRY));
        f *= G3.smooth(95, 130, (float) dist(x, z, PALACE));   // freier Blick auf Schloss und See
        f *= G3.smooth(24, 30, (float) dist(x, z, RAVINE));
        f *= G3.smooth(4, 8, (float) pathDist(x, z));
        f *= 1 - brightness(x, z) * 0.7f;
        // Freie Sicht vom Café über das Klippenplateau aufs Meer
        f *= 1 - (float) (smooth(150, 178, z) * smooth(-60, -20, x) * (1 - smooth(90, 130, x)));
        if (h > 70) f *= G3.smooth(110, 70, h);
        return f;
    }

    static double lerp(double a, double b, double t) { return a + (b - a) * Math.max(0, Math.min(1, t)); }

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
