package daemmerwald;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.*;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import jme3tools.optimize.GeometryBatchFactory;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/** Endlose Welt: lädt 128-m-Kacheln um den Spieler nach und entfernt ferne Kacheln. */
public class Chunks {
    public static final int SIZE = 128, RES = 128;
    /** Splat-Texel liegen genau auf den Gitterpunkten (RES+1 je Seite), damit benachbarte Kacheln an der Grenze denselben Wert haben. */
    private static final int AS = RES + 1;
    private static final float TILE = AS / (float) RES;
    private static final int LOAD_R = 2, KEEP_R = 3;

    private final Node root = new Node("chunks");
    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "chunk-gen");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final ConcurrentLinkedQueue<Runnable> mainQueue = new ConcurrentLinkedQueue<>();

    private final Material terrainBase, trunkMat, leavesMat, deadMat, grassMat, flowerMat, rockMat, farTreeMat, farTrunkMat, runeMat, stoneMat;
    private final Mesh trunkMesh, leavesMesh, farTree, grassMesh, flowerMesh;
    private final Mesh pineMesh = G3.pine(), fernMesh = G3.crossQuads(1.6f, 1.15f), bushMesh = G3.crossQuads(2.4f, 1.7f),
        reedMesh = G3.crossQuads(1.0f, 1.9f), lilyMesh = new com.jme3.scene.shape.Quad(1.1f, 1.1f);
    private final Material[] leafVariants = new Material[3];
    private Material pineMat, fernMat, bushMat, reedMat, lilyMat, logMat, capMat, capMat2, stemMat, lilyFlowerMat;
    private final Mesh[] rocks = new Mesh[4];

    /** Eine sammelbare Zutat in der Welt. */
    public static class Spot {
        public final String key;
        public final Item item;
        /** Baustoff statt Zutat (dann ist item null). */
        public Baustoff mat;
        public final Vector3f pos;
        public Geometry geo;
        Spot(String key, Item item, Vector3f pos) { this.key = key; this.item = item; this.pos = pos; }
    }

    /** Bereits gesammelte Zutaten (wird vom Spiel befüllt, thread-sicher). */
    public final Set<String> picked = ConcurrentHashMap.newKeySet();
    public static final List<House> STORY_HOUSES = House.storyHouses();
    private final Map<Item, Material> itemMats = new EnumMap<>(Item.class);
    private final Map<Item, Mesh> itemMeshes = new EnumMap<>(Item.class);
    private final Map<Baustoff, Material> matMats = new EnumMap<>(Baustoff.class);
    private final Map<Baustoff, Mesh> matMeshes = new EnumMap<>(Baustoff.class);

    static class Chunk {
        volatile List<Spot> spots = List.of();
        volatile List<House> houses = List.of();
        final int cx, cz;
        volatile Node terrain, far, near;
        volatile boolean building, buildingNear;
        final List<float[]> obstacles = new ArrayList<>();
        Chunk(int cx, int cz) { this.cx = cx; this.cz = cz; }
    }

    public Chunks(Node world) {
        world.attachChild(root);
        terrainBase = new Material(G3.am, "Common/MatDefs/Terrain/TerrainLighting.j3md");
        setTex(terrainBase, "DiffuseMap", "Textures/Terrain/PBR/Ground037_1K_Color.png", "DiffuseMap_0_scale", 30f * TILE);
        setTex(terrainBase, "DiffuseMap_1", "Textures/Terrain/splat/dirt.jpg", "DiffuseMap_1_scale", 52f * TILE);
        setTex(terrainBase, "DiffuseMap_2", "Textures/Terrain/PBR/Rock035_1K_Color.jpg", "DiffuseMap_2_scale", 22f * TILE);
        setTex(terrainBase, "DiffuseMap_3", "Textures/Terrain/splat/grass.jpg", "DiffuseMap_3_scale", 48f * TILE);
        Texture n0 = G3.am.loadTexture("Textures/Terrain/splat/dirt_normal.png");
        n0.setWrap(Texture.WrapMode.Repeat);
        terrainBase.setTexture("NormalMap", n0);
        Texture n2 = G3.am.loadTexture("Textures/Terrain/Rock/Rock_normal.png");
        n2.setWrap(Texture.WrapMode.Repeat);
        terrainBase.setTexture("NormalMap_2", n2);
        Texture n3 = G3.am.loadTexture("Textures/Terrain/splat/grass_normal.jpg");
        n3.setWrap(Texture.WrapMode.Repeat);
        terrainBase.setTexture("NormalMap_3", n3);
        terrainBase.setFloat("Shininess", 8f);
        terrainBase.setColor("Specular", ColorRGBA.Black);
        terrainBase.setColor("Diffuse", ColorRGBA.White);
        terrainBase.setColor("Ambient", ColorRGBA.White);

        Spatial tree = G3.am.loadModel("Models/Tree/Tree.mesh.j3o");
        Geometry[] parts = new Geometry[2];
        tree.depthFirstTraversal(s -> {
            if (s instanceof Geometry g) {
                boolean leaves = g.getMaterial().getAdditionalRenderState().getBlendMode() != RenderState.BlendMode.Off
                    || String.valueOf(g.getMaterial().getAssetName()).contains("Leaves")
                    || String.valueOf(g.getMaterial().getParam("DiffuseMap")).contains("Leaves");
                parts[leaves ? 1 : 0] = g;
            }
        });
        trunkMesh = parts[0].getMesh();
        trunkMat = parts[0].getMaterial();
        leavesMesh = parts[1].getMesh();
        leavesMat = parts[1].getMaterial();
        leavesMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        ColorRGBA[] tints = {new ColorRGBA(1, 1, 1, 1), new ColorRGBA(1.2f, 1.15f, 0.65f, 1), new ColorRGBA(1.45f, 0.78f, 0.38f, 1)};
        for (int i = 0; i < 3; i++) {
            leafVariants[i] = leavesMat.clone();
            leafVariants[i].setBoolean("UseMaterialColors", true);
            leafVariants[i].setColor("Diffuse", tints[i]);
            leafVariants[i].setColor("Ambient", tints[i]);
        }
        pineMat = G3.lit(new ColorRGBA(0.95f, 1f, 0.95f, 1));
        pineMat.setTexture("DiffuseMap", G3.needleTex());
        pineMat.setFloat("AlphaDiscardThreshold", 0.3f);
        pineMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        fernMat = decoMat(G3.fernTex(), new ColorRGBA(0.9f, 1f, 0.85f, 1));
        bushMat = decoMat(canopyTex(), new ColorRGBA(1.35f, 1.5f, 1.2f, 1));
        reedMat = decoMat(G3.reedTex(), ColorRGBA.White);
        lilyMat = decoMat(G3.lilyTex(), ColorRGBA.White);
        logMat = G3.litTex("Models/Tree/BarkColor.jpg", new ColorRGBA(0.55f, 0.6f, 0.42f, 1), null);
        capMat = G3.litGlow(new ColorRGBA(0.75f, 0.18f, 0.12f, 1), new ColorRGBA(0.05f, 0.01f, 0, 1));
        capMat2 = G3.lit(new ColorRGBA(0.55f, 0.38f, 0.22f, 1));
        stemMat = G3.lit(new ColorRGBA(0.92f, 0.88f, 0.8f, 1));
        lilyFlowerMat = G3.litGlow(new ColorRGBA(1f, 0.75f, 0.85f, 1), new ColorRGBA(0.15f, 0.08f, 0.1f, 1));
        deadMat = trunkMat.clone();
        deadMat.setColor("Diffuse", new ColorRGBA(0.32f, 0.27f, 0.34f, 1));
        deadMat.setColor("Ambient", new ColorRGBA(0.32f, 0.27f, 0.34f, 1));
        deadMat.setBoolean("UseMaterialColors", true);
        deadMat.setColor("GlowColor", new ColorRGBA(0.10f, 0.02f, 0.14f, 1));

        grassMesh = G3.crossQuads(1.1f, 0.75f);
        grassMat = G3.lit(new ColorRGBA(0.9f, 0.95f, 0.85f, 1));
        grassMat.setTexture("DiffuseMap", G3.grassTex());
        grassMat.setFloat("AlphaDiscardThreshold", 0.4f);
        grassMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        flowerMesh = G3.crossQuads(0.9f, 0.6f);
        flowerMat = G3.lit(ColorRGBA.White);
        flowerMat.setTexture("DiffuseMap", G3.flowerTex());
        flowerMat.setFloat("AlphaDiscardThreshold", 0.4f);
        flowerMat.setColor("GlowColor", new ColorRGBA(0.12f, 0.1f, 0.05f, 1));
        flowerMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        rockMat = G3.litTex("Textures/Terrain/PBR/Rock035_1K_Color.jpg", new ColorRGBA(0.85f, 0.85f, 0.85f, 1), null);
        for (int i = 0; i < rocks.length; i++) rocks[i] = G3.rock(i * 7L + 1, 1f);
        stoneMat = G3.litTex("Textures/Terrain/BrickWall/BrickWall.jpg", new ColorRGBA(0.7f, 0.7f, 0.72f, 1), "Textures/Terrain/BrickWall/BrickWall_normal.jpg");
        runeMat = G3.emissive(new ColorRGBA(0.4f, 0.8f, 1f, 1));

        farTree = makeFarTree();
        farTreeMat = G3.lit(new ColorRGBA(0.75f, 0.85f, 0.7f, 1));
        farTreeMat.setTexture("DiffuseMap", canopyTex());
        farTreeMat.setFloat("AlphaDiscardThreshold", 0.45f);
        farTreeMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        farTrunkMat = G3.lit(new ColorRGBA(0.22f, 0.17f, 0.13f, 1));
        for (Item it : Item.values()) {
            ColorRGBA c = G3.rgb(it.color);
            itemMats.put(it, G3.litGlow(c, c.mult(0.7f)));
        }
        itemMeshes.put(Item.MONDBLUME, new com.jme3.scene.shape.Sphere(4, 6, 0.22f));
        itemMeshes.put(Item.KROETENMOOS, new com.jme3.scene.shape.Sphere(6, 8, 0.28f));
        itemMeshes.put(Item.ASCHWURZ, new com.jme3.scene.shape.Cylinder(2, 5, 0.06f, 0.01f, 0.7f, true, false));
        itemMeshes.put(Item.STERNKRISTALL, new com.jme3.scene.shape.Sphere(3, 4, 0.22f));
        itemMeshes.put(Item.SEEROSE, new com.jme3.scene.shape.Sphere(4, 8, 0.25f));
        itemMeshes.put(Item.FEDER, new com.jme3.scene.shape.Box(0.04f, 0.3f, 0.01f));
        // Baustoffe: Holzbündel, Gesteinsbrocken, Lehmklumpen – mit leichtem Schimmer, damit man sie findet
        Material wood = G3.litTex("Models/Tree/BarkColor.jpg", new ColorRGBA(1f, 0.85f, 0.7f, 1), null);
        wood.setColor("GlowColor", new ColorRGBA(0.12f, 0.07f, 0.02f, 1));
        matMats.put(Baustoff.HOLZ, wood);
        Material stone = G3.litTex("Textures/Terrain/Rocky/RockyTexture.jpg", new ColorRGBA(0.85f, 0.85f, 0.88f, 1), null);
        stone.setColor("GlowColor", new ColorRGBA(0.06f, 0.06f, 0.08f, 1));
        matMats.put(Baustoff.STEIN, stone);
        matMats.put(Baustoff.LEHM, G3.litGlow(new ColorRGBA(0.62f, 0.36f, 0.22f, 1), new ColorRGBA(0.12f, 0.05f, 0.02f, 1)));
        matMeshes.put(Baustoff.HOLZ, new com.jme3.scene.shape.Cylinder(2, 8, 0.13f, 1.0f, true));
        matMeshes.put(Baustoff.STEIN, rocks[0]);
        matMeshes.put(Baustoff.LEHM, new com.jme3.scene.shape.Sphere(8, 10, 0.3f));
    }

    private static Material decoMat(Texture2D t, ColorRGBA tint) {
        Material m = G3.lit(tint);
        m.setTexture("DiffuseMap", t);
        m.setFloat("AlphaDiscardThreshold", 0.4f);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        return m;
    }

    private void setTex(Material m, String name, String path, String scaleName, float scale) {
        Texture t = G3.am.loadTexture(path);
        t.setWrap(Texture.WrapMode.Repeat);
        m.setTexture(name, t);
        m.setFloat(scaleName, scale);
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    // ---------------------------------------------------------------- Laden

    /** Lädt die Umgebung sofort (beim Start), ohne Hintergrund-Thread. */
    public void preload(float x, float z) {
        int pcx = Math.floorDiv((int) x, SIZE), pcz = Math.floorDiv((int) z, SIZE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk c = chunks.get(key(pcx + dx, pcz + dz));
                if (c != null && (c.building || c.buildingNear)) continue;
                if (c == null) {
                    c = new Chunk(pcx + dx, pcz + dz);
                    chunks.put(key(c.cx, c.cz), c);
                }
                if (c.terrain == null) buildBase(c);
                if (c.near == null) buildNear(c);
                attach(c);
            }
        }
    }

    public void update(float x, float z) {
        Runnable r;
        int done = 0;
        while (done < 3 && (r = mainQueue.poll()) != null) { r.run(); done++; }
        int pcx = Math.floorDiv((int) x, SIZE), pcz = Math.floorDiv((int) z, SIZE);
        for (int dx = -LOAD_R; dx <= LOAD_R; dx++) {
            for (int dz = -LOAD_R; dz <= LOAD_R; dz++) {
                int cx = pcx + dx, cz = pcz + dz;
                Chunk c = chunks.computeIfAbsent(key(cx, cz), k -> new Chunk(cx, cz));
                if (c.terrain == null && !c.building) {
                    c.building = true;
                    pool.submit(() -> {
                        try {
                            buildBase(c);
                            mainQueue.add(() -> attach(c));
                        } catch (Throwable t) { t.printStackTrace(); }
                    });
                }
                boolean wantNear = Math.max(Math.abs(dx), Math.abs(dz)) <= 1;
                if (wantNear && c.near == null && !c.buildingNear && c.terrain != null) {
                    c.buildingNear = true;
                    pool.submit(() -> {
                        try {
                            buildNear(c);
                            mainQueue.add(() -> attach(c));
                        } catch (Throwable t) { t.printStackTrace(); }
                    });
                }
                if (c.near != null && c.far != null) {
                    boolean nearOn = wantNear;
                    c.near.setCullHint(nearOn ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
                    c.far.setCullHint(nearOn ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
                }
            }
        }
        for (Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator(); it.hasNext(); ) {
            Chunk c = it.next().getValue();
            int d = Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz));
            if (d > KEEP_R && !c.building && !c.buildingNear) {
                if (c.terrain != null) c.terrain.removeFromParent();
                if (c.far != null) c.far.removeFromParent();
                if (c.near != null) c.near.removeFromParent();
                it.remove();
            } else if (d > 1 && c.near != null && !c.buildingNear) {
                c.near.removeFromParent();
                c.near = null;
            }
        }
    }

    private void attach(Chunk c) {
        if (!chunks.containsKey(key(c.cx, c.cz))) return;
        if (c.terrain != null && c.terrain.getParent() == null) root.attachChild(c.terrain);
        if (c.far != null && c.far.getParent() == null) root.attachChild(c.far);
        if (c.near != null && c.near.getParent() == null) {
            root.attachChild(c.near);
            if (c.far != null) c.far.setCullHint(Spatial.CullHint.Always);
        }
        c.building = false;
        c.buildingNear = false;
    }

    /** Häuser der Wildnis in der Nähe. */
    public List<House> housesNear(float x, float z) {
        List<House> out = new ArrayList<>();
        int cx = Math.floorDiv((int) Math.floor(x), SIZE), cz = Math.floorDiv((int) Math.floor(z), SIZE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            Chunk c = chunks.get(key(cx + dx, cz + dz));
            if (c != null) out.addAll(c.houses);
        }
        return out;
    }

    /** Zutaten in der Nähe (nur geladene Nah-Kacheln). */
    public List<Spot> spotsNear(float x, float z, float r) {
        List<Spot> out = new ArrayList<>();
        int cx = Math.floorDiv((int) Math.floor(x), SIZE), cz = Math.floorDiv((int) Math.floor(z), SIZE);
        int reach = (int) Math.ceil(r / SIZE);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                Chunk c = chunks.get(key(cx + dx, cz + dz));
                if (c == null || c.near == null || c.near.getParent() == null) continue;
                for (Spot s : c.spots) {
                    if (s.geo != null && s.geo.getParent() != null && FastMath.abs(s.pos.x - x) < r && FastMath.abs(s.pos.z - z) < r) out.add(s);
                }
            }
        }
        return out;
    }

    public boolean blocked(float x, float z, float r) {
        int cx = Math.floorDiv((int) Math.floor(x), SIZE), cz = Math.floorDiv((int) Math.floor(z), SIZE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk c = chunks.get(key(cx + dx, cz + dz));
                if (c == null) continue;
                synchronized (c.obstacles) {
                    for (float[] o : c.obstacles) {
                        float ddx = x - o[0], ddz = z - o[1], rr = r + o[2];
                        if (ddx * ddx + ddz * ddz < rr * rr) return true;
                    }
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Bauen

    private void buildBase(Chunk c) {
        float x0 = c.cx * SIZE, z0 = c.cz * SIZE;
        int n = RES + 1;
        float step = (float) SIZE / RES;
        float[] h = new float[(n + 2) * (n + 2)];
        for (int j = 0; j < n + 2; j++)
            for (int i = 0; i < n + 2; i++)
                h[j * (n + 2) + i] = WorldGen.height(x0 + (i - 1) * step, z0 + (j - 1) * step);

        float[] pos = new float[n * n * 3], nor = new float[n * n * 3], uv = new float[n * n * 2], tan = new float[n * n * 4];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int v = j * n + i;
                float hh = h[(j + 1) * (n + 2) + (i + 1)];
                pos[v * 3] = x0 + i * step;
                pos[v * 3 + 1] = hh;
                pos[v * 3 + 2] = z0 + j * step;
                float hl = h[(j + 1) * (n + 2) + i], hr = h[(j + 1) * (n + 2) + i + 2];
                float hd = h[j * (n + 2) + i + 1], hu = h[(j + 2) * (n + 2) + i + 1];
                Vector3f nn = new Vector3f(hl - hr, 2 * step, hd - hu).normalizeLocal();
                nor[v * 3] = nn.x; nor[v * 3 + 1] = nn.y; nor[v * 3 + 2] = nn.z;
                Vector3f t = new Vector3f(2 * step, hr - hl, 0).normalizeLocal();
                tan[v * 4] = t.x; tan[v * 4 + 1] = t.y; tan[v * 4 + 2] = t.z; tan[v * 4 + 3] = 1;
                uv[v * 2] = (i + 0.5f) / AS;
                uv[v * 2 + 1] = (j + 0.5f) / AS;
            }
        }
        short[] idx = new short[RES * RES * 6];
        int q = 0;
        for (int j = 0; j < RES; j++) {
            for (int i = 0; i < RES; i++) {
                int a = j * n + i, b = a + n;
                idx[q++] = (short) a; idx[q++] = (short) b; idx[q++] = (short) (a + 1);
                idx[q++] = (short) (a + 1); idx[q++] = (short) b; idx[q++] = (short) (b + 1);
            }
        }
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, pos);
        m.setBuffer(VertexBuffer.Type.Normal, 3, nor);
        m.setBuffer(VertexBuffer.Type.Tangent, 4, tan);
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        m.setBuffer(VertexBuffer.Type.Index, 3, idx);
        m.updateBound();

        // Splatmap: R Waldboden, G Kies/Pfad, B Fels, A Gras
        int as = AS;
        ByteBuffer alpha = BufferUtils.createByteBuffer(as * as * 4);
        for (int j = 0; j < as; j++) {
            for (int i = 0; i < as; i++) {
                double wx = x0 + i * step, wz = z0 + j * step;
                int gi = i, gj = j;
                float hh = h[(gj + 1) * (n + 2) + gi + 1];
                float slope = Math.abs(h[(gj + 1) * (n + 2) + gi + 2] - h[(gj + 1) * (n + 2) + gi]) / (2 * step)
                    + Math.abs(h[(gj + 2) * (n + 2) + gi + 1] - h[gj * (n + 2) + gi + 1]) / (2 * step);
                float rock = G3.smooth(0.75f, 1.25f, slope);
                double pd = WorldGen.pathDist(wx, wz);
                float edge = (float) G3.fbm(wx * 0.25 + 3, wz * 0.25, 2);
                float path = Math.hypot(wx, wz) < 300 ? G3.smooth(3.4f + edge * 2.6f, 0.6f, (float) pd) * 0.92f : 0;
                if (hh < 1.3f && hh > -3f) path = Math.max(path, G3.smooth(1.3f, 0.4f, hh));
                if (WorldGen.dist(wx, wz, WorldGen.QUARRY) < 24 || WorldGen.dist(wx, wz, WorldGen.RAVINE) < 25) rock = Math.max(rock, 0.55f);
                float bright = WorldGen.brightness(wx, wz);
                float meadowNoise = (float) G3.fbm(wx * 0.03 + 7, wz * 0.03, 3);
                float grass = Math.max(bright * 0.85f, G3.smooth(0.5f, 0.7f, meadowNoise) * 0.7f) * (1 - WorldGen.darkness(wx, wz));
                grass *= 1 - rock;
                float rest = Math.max(0, 1 - rock - path);
                float r = rest * (1 - grass), a = rest * grass;
                alpha.put((byte) (r * 255)).put((byte) (path * (1 - rock) * 255)).put((byte) (rock * 255)).put((byte) (a * 255));
            }
        }
        alpha.flip();
        Texture2D at = new Texture2D(new Image(Image.Format.RGBA8, as, as, alpha, ColorSpace.Linear));
        at.setMagFilter(Texture.MagFilter.Bilinear);
        at.setWrap(Texture.WrapMode.EdgeClamp);
        Material tm = terrainBase.clone();
        tm.setTexture("AlphaMap", at);
        Geometry tg = G3.geom("terrain_" + c.cx + "_" + c.cz, m, tm);
        tg.setShadowMode(ShadowMode.Receive);
        Node tn = new Node("t");
        tn.attachChild(tg);

        // Ferne Vegetation + Hindernisse
        Node far = new Node("far");
        Node farBatch = new Node("farBatch");
        List<float[]> obst = new ArrayList<>();
        Random rnd = new Random(key(c.cx, c.cz) * 31 + 7);
        // Begehbare Häuser in der Wildnis
        List<House> hs = new ArrayList<>();
        Random hr = new Random(key(c.cx, c.cz) * 97 + 13);
        if (Math.hypot(x0 + 64, z0 + 64) > 330 && hr.nextFloat() < 0.22f) {
            for (int tries = 0; tries < 6; tries++) {
                float hx = x0 + 20 + hr.nextFloat() * 88, hz = z0 + 20 + hr.nextFloat() * 88;
                float h0 = WorldGen.height(hx, hz);
                float sl = Math.abs(WorldGen.height(hx + 4, hz) - h0) + Math.abs(WorldGen.height(hx, hz + 4) - h0);
                if (h0 < WorldGen.WATER + 1.2f || sl > 2.2f || h0 > 90) continue;
                House wh = House.random("w" + c.cx + "_" + c.cz, hx, hz, hr);
                hs.add(wh);
                obst.add(new float[]{hx, hz, wh.radius(), 5, h0, 1, 0, 0});
                far.attachChild(wh.buildExterior());
                break;
            }
        }
        // Rast-Cafés: in der Wildnis tauchen immer wieder welche auf (dort kann man speichern)
        if (Math.hypot(x0 + 64, z0 + 64) > 300 && hr.nextFloat() < 0.14f) {
            for (int tries = 0; tries < 6; tries++) {
                float hx = x0 + 20 + hr.nextFloat() * 88, hz = z0 + 20 + hr.nextFloat() * 88;
                float h0 = WorldGen.height(hx, hz);
                float sl = Math.abs(WorldGen.height(hx + 4, hz) - h0) + Math.abs(WorldGen.height(hx, hz + 4) - h0);
                if (h0 < WorldGen.WATER + 1.2f || sl > 2.2f || h0 > 90 || WorldGen.darkness(hx, hz) > 0.3f) continue;
                boolean clash = false;
                for (House o : hs) if (FastMath.sqr(o.x - hx) + FastMath.sqr(o.z - hz) < 400) clash = true;
                if (clash) continue;
                House ch = House.randomCafe("c" + c.cx + "_" + c.cz, hx, hz, hr);
                hs.add(ch);
                obst.add(new float[]{hx, hz, ch.radius(), 5, h0, 1, 0, 0});
                far.attachChild(ch.buildExterior());
                break;
            }
        }
        for (House sh : STORY_HOUSES) if (sh.x > x0 - 10 && sh.x < x0 + SIZE + 10 && sh.z > z0 - 10 && sh.z < z0 + SIZE + 10) hs.add(sh);
        for (float gz = 3; gz < SIZE; gz += 7) {
            for (float gx = 3; gx < SIZE; gx += 7) {
                float wx = x0 + gx + (rnd.nextFloat() - 0.5f) * 5.5f, wz = z0 + gz + (rnd.nextFloat() - 0.5f) * 5.5f;
                float hh = WorldGen.height(wx, wz);
                float slope = Math.abs(WorldGen.height(wx + 1, wz) - hh) + Math.abs(WorldGen.height(wx, wz + 1) - hh);
                float f = WorldGen.forest(wx, wz, hh, slope);
                float roll = rnd.nextFloat();
                float scale = 3.4f + rnd.nextFloat() * 2.4f;
                float rot = rnd.nextFloat() * FastMath.TWO_PI;
                boolean nearHouse = false;
                for (House nh : hs) if (FastMath.sqr(wx - nh.x) + FastMath.sqr(wz - nh.z) < FastMath.sqr(nh.radius() + 4)) nearHouse = true;
                if (nearHouse) continue;
                if (roll < f * 0.9f) {
                    boolean dead = WorldGen.darkness(wx, wz) > 0.45f && rnd.nextFloat() < 0.75f;
                    float pineN = (float) G3.fbm(wx * 0.006 + 50, wz * 0.006 - 20, 3) + (hh > 14 ? 0.25f : 0);
                    boolean pine = !dead && pineN > 0.58f && rnd.nextFloat() < 0.85f;
                    float autumn = (float) G3.fbm(wx * 0.004 - 20, wz * 0.004 + 5, 3);
                    int variant = autumn > 0.63f ? 2 : autumn > 0.5f ? 1 : 0;
                    if (pine) {
                        float ps = 9 + rnd.nextFloat() * 9;
                        obst.add(new float[]{wx, wz, 0.35f, 4, hh, ps, rot, 0});
                        Geometry g = new Geometry("pine", pineMesh);
                        g.setMaterial(pineMat);
                        g.setLocalTranslation(wx, hh - 0.2f, wz);
                        g.setLocalScale(ps * 0.75f, ps, ps * 0.75f);
                        g.setLocalRotation(new Quaternion().fromAngleAxis(rot, Vector3f.UNIT_Y));
                        farBatch.attachChild(g);
                        continue;
                    }
                    obst.add(new float[]{wx, wz, 0.22f * scale, dead ? 1 : 0, hh, scale, rot, variant});
                    if (!dead) {
                        Geometry g = new Geometry("ft", farTree);
                        g.setMaterial(farTreeMat);
                        g.setLocalTranslation(wx, hh - 0.2f, wz);
                        g.setLocalScale(scale / 4.4f);
                        g.setLocalRotation(new Quaternion().fromAngleAxis(rot, Vector3f.UNIT_Y));
                        farBatch.attachChild(g);
                    }
                } else if (roll > 0.985f - G3.smooth(0.6f, 1.4f, slope) * 0.05f && hh > WorldGen.WATER - 1) {
                    float rs = 0.6f + rnd.nextFloat() * 1.6f;
                    obst.add(new float[]{wx, wz, rs * 0.9f, 2, hh, rs, rot, 0});
                    Geometry g = new Geometry("rock", rocks[rnd.nextInt(rocks.length)]);
                    g.setMaterial(rockMat);
                    g.setLocalTranslation(wx, hh - 0.2f * rs, wz);
                    g.setLocalScale(rs);
                    g.setLocalRotation(new Quaternion().fromAngleAxis(rot, Vector3f.UNIT_Y));
                    farBatch.attachChild(g);
                }
            }
        }
        // Gelegentliche Steinkreise mit Runen in der Wildnis
        if (Math.hypot(x0 + 64, z0 + 64) > 330 && rnd.nextFloat() < 0.18f) {
            float sx = x0 + 30 + rnd.nextFloat() * 68, sz = z0 + 30 + rnd.nextFloat() * 68;
            float sh = WorldGen.height(sx, sz);
            if (sh > WorldGen.WATER + 1 && sh < 60) {
                for (int k = 0; k < 7; k++) {
                    double a = k * Math.PI * 2 / 7;
                    float px = sx + (float) Math.cos(a) * 7, pz = sz + (float) Math.sin(a) * 7;
                    float ph = WorldGen.height(px, pz);
                    Geometry st = G3.geom("menhir", new com.jme3.scene.shape.Box(0.6f, 2.2f, 0.4f), stoneMat);
                    st.setLocalTranslation(px, ph + 1.6f, pz);
                    st.setLocalRotation(new Quaternion().fromAngles(0.06f * (k % 3 - 1), (float) a, 0.05f));
                    farBatch.attachChild(st);
                    Geometry rune = G3.geom("rune", new com.jme3.scene.shape.Box(0.08f, 0.6f, 0.02f), runeMat);
                    rune.setLocalTranslation(px - (float) Math.cos(a) * 0.42f, ph + 2.2f, pz - (float) Math.sin(a) * 0.42f);
                    rune.setLocalRotation(new Quaternion().fromAngles(0, (float) a, 0));
                    farBatch.attachChild(rune);
                    obst.add(new float[]{px, pz, 0.8f, 3, ph, 1, 0, 0});
                }
            }
        }
        GeometryBatchFactory.optimize(farBatch);
        farBatch.setShadowMode(ShadowMode.CastAndReceive);
        far.attachChild(farBatch);
        synchronized (c.obstacles) {
            c.obstacles.clear();
            c.obstacles.addAll(obst);
        }
        c.terrain = tn;
        c.far = far;
        List<House> own = new ArrayList<>();
        for (House oh : hs) if (!STORY_HOUSES.contains(oh)) own.add(oh);
        c.houses = own;
    }

    private void buildNear(Chunk c) {
        if (c.terrain == null) buildBase(c);
        float x0 = c.cx * SIZE, z0 = c.cz * SIZE;
        Node near = new Node("near");
        Node trees = new Node("trees");
        List<float[]> obs;
        synchronized (c.obstacles) { obs = new ArrayList<>(c.obstacles); }
        for (float[] o : obs) {
            if (o[3] == 0 || o[3] == 1) {
                Quaternion q = new Quaternion().fromAngleAxis(o[6], Vector3f.UNIT_Y);
                Geometry t = new Geometry("trunk", trunkMesh);
                t.setMaterial(o[3] == 1 ? deadMat : trunkMat);
                t.setLocalTranslation(o[0], o[4] - 0.3f, o[1]);
                t.setLocalScale(o[5]);
                t.setLocalRotation(q);
                trees.attachChild(t);
                if (o[3] == 0) {
                    Geometry l = new Geometry("leaves", leavesMesh);
                    l.setMaterial(leafVariants[(int) o[7]]);
                    l.setLocalTranslation(o[0], o[4] - 0.3f, o[1]);
                    l.setLocalScale(o[5]);
                    l.setLocalRotation(q);
                    trees.attachChild(l);
                }
            } else if (o[3] == 4) {
                Geometry g = new Geometry("pine", pineMesh);
                g.setMaterial(pineMat);
                g.setLocalTranslation(o[0], o[4] - 0.2f, o[1]);
                g.setLocalScale(o[5] * 0.75f, o[5], o[5] * 0.75f);
                g.setLocalRotation(new Quaternion().fromAngleAxis(o[6], Vector3f.UNIT_Y));
                trees.attachChild(g);
            } else if (o[3] == 2) {
                Geometry g = new Geometry("rock", rocks[(int) (Math.abs(o[0] * 13 + o[1] * 7)) % rocks.length]);
                g.setMaterial(rockMat);
                g.setLocalTranslation(o[0], o[4] - 0.2f * o[5], o[1]);
                g.setLocalScale(o[5]);
                g.setLocalRotation(new Quaternion().fromAngleAxis(o[6], Vector3f.UNIT_Y));
                trees.attachChild(g);
            }
        }
        // Gras & Blumen
        Node grass = new Node("grass");
        Random rnd = new Random(key(c.cx, c.cz) * 17 + 3);
        for (float gz = 1; gz < SIZE; gz += 2.6f) {
            for (float gx = 1; gx < SIZE; gx += 2.6f) {
                float wx = x0 + gx + (rnd.nextFloat() - 0.5f) * 2.4f, wz = z0 + gz + (rnd.nextFloat() - 0.5f) * 2.4f;
                float hh = WorldGen.height(wx, wz);
                if (hh < WorldGen.WATER + 0.4f) continue;
                float bright = WorldGen.brightness(wx, wz), dark = WorldGen.darkness(wx, wz);
                double pd = Math.hypot(wx, wz) < 300 ? WorldGen.pathDist(wx, wz) : 99;
                float meadow = (float) G3.fbm(wx * 0.03 + 7, wz * 0.03, 3);
                float p = Math.max(bright, G3.smooth(0.45f, 0.7f, meadow)) * (1 - dark * 0.8f);
                if (pd < 2.5) p *= 0.1f;
                float roll = rnd.nextFloat();
                if (roll < p * 0.75f) {
                    boolean flower = rnd.nextFloat() < bright * 0.35f;
                    Geometry g = new Geometry(flower ? "fl" : "gr", flower ? flowerMesh : grassMesh);
                    g.setMaterial(flower ? flowerMat : grassMat);
                    g.setLocalTranslation(wx, hh - 0.05f, wz);
                    g.setLocalScale(0.8f + rnd.nextFloat() * 0.7f);
                    g.setLocalRotation(new Quaternion().fromAngleAxis(rnd.nextFloat() * 3f, Vector3f.UNIT_Y));
                    grass.attachChild(g);
                }
            }
        }
        // Unterholz & Details: Farne, Büsche, Baumstämme, Pilze, Schilf, Seerosen, Steinchen
        Random dr = new Random(key(c.cx, c.cz) * 13 + 5);
        for (float gz = 1.5f; gz < SIZE; gz += 3.4f) {
            for (float gx = 1.5f; gx < SIZE; gx += 3.4f) {
                float wx = x0 + gx + (dr.nextFloat() - 0.5f) * 3, wz = z0 + gz + (dr.nextFloat() - 0.5f) * 3;
                float hh = WorldGen.height(wx, wz);
                float roll = dr.nextFloat(), rot = dr.nextFloat() * FastMath.TWO_PI;
                Quaternion q = new Quaternion().fromAngleAxis(rot, Vector3f.UNIT_Y);
                if (hh < WorldGen.WATER - 0.4f && hh > WorldGen.WATER - 3f && WorldGen.sea(wx, wz) < 0.2) {
                    if (roll < 0.22f) {
                        Geometry g = deco("lily", lilyMesh, lilyMat, wx, WorldGen.WATER + 0.03f, wz, 0.6f + dr.nextFloat() * 0.6f);
                        g.setLocalRotation(new Quaternion().fromAngles(-FastMath.HALF_PI, rot, 0));
                        grass.attachChild(g);
                        if (dr.nextFloat() < 0.3f) grass.attachChild(deco("lilyFlower", new com.jme3.scene.shape.Sphere(6, 8, 0.12f), lilyFlowerMat, wx + 0.2f, WorldGen.WATER + 0.1f, wz + 0.2f, 1));
                    }
                    continue;
                }
                if (hh < WorldGen.WATER + 0.05f) continue;
                if (hh < WorldGen.WATER + 1.0f && roll < 0.45f) {
                    Geometry g = deco("reed", reedMesh, reedMat, wx, hh - 0.1f, wz, 0.8f + dr.nextFloat() * 0.6f);
                    g.setLocalRotation(q);
                    grass.attachChild(g);
                    continue;
                }
                float slope = Math.abs(WorldGen.height(wx + 1, wz) - hh) + Math.abs(WorldGen.height(wx, wz + 1) - hh);
                float f = WorldGen.forest(wx, wz, hh, slope);
                double pd = Math.hypot(wx, wz) < 300 ? WorldGen.pathDist(wx, wz) : 99;
                if (pd < 3) continue;
                if (f > 0.3f && roll < 0.22f) {
                    Geometry g = deco("fern", fernMesh, fernMat, wx, hh - 0.05f, wz, 0.7f + dr.nextFloat() * 0.7f);
                    g.setLocalRotation(q);
                    grass.attachChild(g);
                } else if (f > 0.15f && roll < 0.29f) {
                    Geometry g = deco("bush", bushMesh, bushMat, wx, hh - 0.15f, wz, 0.7f + dr.nextFloat() * 0.6f);
                    g.setLocalRotation(q);
                    grass.attachChild(g);
                } else if (f > 0.3f && roll < 0.302f) {
                    float len = 4 + dr.nextFloat() * 5;
                    Geometry g = deco("log", G3.log(len, 0.3f + dr.nextFloat() * 0.15f), logMat, wx, hh + 0.25f, wz, 1);
                    g.setLocalRotation(new Quaternion().fromAngles(0, rot, 0));
                    trees.attachChild(g);
                } else if (f > 0.2f && roll < 0.33f) {
                    Material cap = dr.nextBoolean() ? capMat : capMat2;
                    for (int k = 0; k < 3 + dr.nextInt(4); k++) {
                        float mx = wx + (dr.nextFloat() - 0.5f) * 0.9f, mz = wz + (dr.nextFloat() - 0.5f) * 0.9f, ms = 0.5f + dr.nextFloat() * 0.8f;
                        float mh = WorldGen.height(mx, mz);
                        Geometry st = deco("mstem", new com.jme3.scene.shape.Cylinder(2, 6, 0.025f, 0.03f, 0.14f, true, false), stemMat, mx, mh + 0.07f * ms, mz, ms);
                        st.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
                        grass.attachChild(st);
                        Geometry cp = deco("mcap", new com.jme3.scene.shape.Dome(Vector3f.ZERO, 5, 10, 0.08f, false), cap, mx, mh + 0.13f * ms, mz, ms);
                        grass.attachChild(cp);
                    }
                } else if (roll > 0.985f) {
                    Geometry g = deco("pebble", rocks[dr.nextInt(rocks.length)], rockMat, wx, hh - 0.05f, wz, 0.15f + dr.nextFloat() * 0.25f);
                    g.setLocalRotation(q);
                    grass.attachChild(g);
                }
            }
        }

        // Zutaten
        List<Spot> spots = new ArrayList<>();
        Node items = new Node("items");
        Random ir = new Random(key(c.cx, c.cz) * 7 + 11);
        int idx = 0;
        for (float gz = 6; gz < SIZE; gz += 15) {
            for (float gx = 6; gx < SIZE; gx += 15) {
                idx++;
                float wx = x0 + gx + (ir.nextFloat() - 0.5f) * 12, wz = z0 + gz + (ir.nextFloat() - 0.5f) * 12;
                float roll = ir.nextFloat();
                String k = c.cx + ":" + c.cz + ":" + idx;
                float hh = WorldGen.height(wx, wz);
                if (hh < WorldGen.WATER - 0.3f || picked.contains(k)) continue;
                float slope = Math.abs(WorldGen.height(wx + 1, wz) - hh) + Math.abs(WorldGen.height(wx, wz + 1) - hh);
                Item it = null;
                if (WorldGen.darkness(wx, wz) > 0.4f && roll < 0.55f) it = Item.ASCHWURZ;
                else if (hh < 0.8f && roll < 0.5f) it = Item.SEEROSE;
                else if (hh < 2.0f && roll < 0.45f) it = Item.KROETENMOOS;
                else if ((slope > 0.7f || hh > 25 || WorldGen.dist(wx, wz, WorldGen.QUARRY) < 30) && roll < 0.5f) it = Item.STERNKRISTALL;
                else if (WorldGen.brightness(wx, wz) > 0.3f && roll < 0.55f) it = Item.MONDBLUME;
                else if (roll < 0.06f) it = Item.FEDER;
                else if (roll < 0.14f) it = Item.MONDBLUME;
                else if (roll < 0.2f) it = Item.KROETENMOOS;
                if (it == null || slope > 1.6f) continue;
                Spot sp = new Spot(k, it, new Vector3f(wx, Math.max(hh, WorldGen.WATER + 0.05f) + 0.35f, wz));
                Geometry g = new Geometry("item", itemMeshes.get(it));
                g.setMaterial(itemMats.get(it));
                g.setLocalTranslation(sp.pos);
                if (it == Item.STERNKRISTALL) g.setLocalScale(1, 2, 1);
                if (it == Item.MONDBLUME || it == Item.SEEROSE) g.setLocalScale(1, 0.4f, 1);
                if (it == Item.KROETENMOOS) g.setLocalScale(1, 0.45f, 1);
                if (it == Item.ASCHWURZ) g.setLocalRotation(new Quaternion().fromAngles(-FastMath.HALF_PI + 0.4f, 0, 0.3f));
                g.setShadowMode(ShadowMode.Off);
                items.attachChild(g);
                sp.geo = g;
                spots.add(sp);
            }
        }
        // Baustoffe fürs eigene Haus: Totholz, Steine, Lehm
        Random br = new Random(key(c.cx, c.cz) * 13 + 5);
        int bidx = 0;
        for (float gz = 9; gz < SIZE; gz += 13) {
            for (float gx = 9; gx < SIZE; gx += 13) {
                bidx++;
                float wx = x0 + gx + (br.nextFloat() - 0.5f) * 10, wz = z0 + gz + (br.nextFloat() - 0.5f) * 10;
                float roll = br.nextFloat();
                String k = "b" + c.cx + ":" + c.cz + ":" + bidx;
                float hh = WorldGen.height(wx, wz);
                if (hh < WorldGen.WATER + 0.15f || picked.contains(k)) continue;
                float slope = Math.abs(WorldGen.height(wx + 1, wz) - hh) + Math.abs(WorldGen.height(wx, wz + 1) - hh);
                if (slope > 1.4f || WorldGen.pathDist(wx, wz) < 2.5f) continue;
                float f = WorldGen.forest(wx, wz, hh, slope);
                Baustoff m = null;
                if (hh < 2.2f && roll < 0.45f) m = Baustoff.LEHM;
                else if ((slope > 0.5f || WorldGen.dist(wx, wz, WorldGen.QUARRY) < 34 || hh > 20) && roll < 0.45f) m = Baustoff.STEIN;
                else if (f > 0.25f && roll < 0.4f) m = Baustoff.HOLZ;
                else if (roll < 0.1f) m = Baustoff.HOLZ;
                else if (roll < 0.15f) m = Baustoff.STEIN;
                if (m == null) continue;
                Spot sp = new Spot(k, null, new Vector3f(wx, hh + 0.12f, wz));
                sp.mat = m;
                Geometry g = new Geometry("mat", matMeshes.get(m));
                g.setMaterial(matMats.get(m));
                g.setLocalTranslation(sp.pos);
                float rot = br.nextFloat() * FastMath.TWO_PI;
                if (m == Baustoff.HOLZ) g.setLocalRotation(new Quaternion().fromAngles(0, rot, FastMath.HALF_PI));
                else g.setLocalRotation(new Quaternion().fromAngleAxis(rot, Vector3f.UNIT_Y));
                if (m == Baustoff.LEHM) g.setLocalScale(1.3f, 0.35f, 1f);
                if (m == Baustoff.STEIN) g.setLocalScale(0.32f);
                g.setShadowMode(ShadowMode.Cast);
                items.attachChild(g);
                sp.geo = g;
                spots.add(sp);
            }
        }
        c.spots = spots;
        near.attachChild(items);
        GeometryBatchFactory.optimize(trees);
        GeometryBatchFactory.optimize(grass);
        trees.setShadowMode(ShadowMode.CastAndReceive);
        grass.setShadowMode(ShadowMode.Receive);
        near.attachChild(trees);
        near.attachChild(grass);
        c.near = near;
    }

    private static Geometry deco(String name, Mesh mesh, Material m, float x, float y, float z, float s) {
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        g.setLocalScale(s);
        return g;
    }

    private static Mesh makeFarTree() {
        // Krone: drei gekreuzte Flächen, Stamm: Prisma
        float[] pos = new float[]{
            -3.2f, 2.6f, 0, 3.2f, 2.6f, 0, 3.2f, 10.5f, 0, -3.2f, 10.5f, 0,
            0, 2.6f, -3.2f, 0, 2.6f, 3.2f, 0, 10.5f, 3.2f, 0, 10.5f, -3.2f,
            -2.3f, 2.6f, -2.3f, 2.3f, 2.6f, 2.3f, 2.3f, 10.5f, 2.3f, -2.3f, 10.5f, -2.3f,
            -0.25f, 0, 0, 0.25f, 0, 0, 0.18f, 4, 0, -0.18f, 4, 0,
            0, 0, -0.25f, 0, 0, 0.25f, 0, 4, 0.18f, 0, 4, -0.18f};
        float[] uv = new float[]{0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1,
            0, 0, 0.02f, 0, 0.02f, 0.02f, 0, 0.02f, 0, 0, 0.02f, 0, 0.02f, 0.02f, 0, 0.02f};
        float[] nor = new float[pos.length];
        for (int i = 0; i < nor.length; i += 3) { nor[i + 1] = 1; }
        short[] idx = {0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7, 8, 9, 10, 8, 10, 11, 12, 13, 14, 12, 14, 15, 16, 17, 18, 16, 18, 19};
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, pos);
        m.setBuffer(VertexBuffer.Type.Normal, 3, nor);
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        m.setBuffer(VertexBuffer.Type.Index, 3, idx);
        m.updateBound();
        return m;
    }

    private static Texture2D canopyTex() {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(58, 40, 28));
        g.fillRect(0, 250, 6, 6);
        Random r = new Random(5);
        for (int i = 0; i < 900; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble());
            double x = 128 + Math.cos(a) * d * 108, y = 120 + Math.sin(a) * d * 110 * 0.95;
            int gr = 70 + r.nextInt(60);
            g.setColor(new java.awt.Color(30 + r.nextInt(25), gr, 30 + r.nextInt(20), 235));
            double s = 6 + r.nextDouble() * 9;
            g.fill(new java.awt.geom.Ellipse2D.Double(x - s / 2, y - s / 2, s, s * 0.7));
        }
        g.dispose();
        return G3.tex(img);
    }
}
