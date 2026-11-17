package daemmerwald;

import com.jme3.audio.AudioBuffer;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioNode;
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/** Spielt die prozedurale Musik und Atmosphäre ab und blendet je nach Situation über. */
public class Music {
    private final Main app;
    private final Map<String, AudioNode> tracks = new HashMap<>();
    private final Map<String, Float> vol = new HashMap<>();
    private final ConcurrentLinkedQueue<Object[]> ready = new ConcurrentLinkedQueue<>();
    private final boolean enabled;
    public static float masterVolume = 0.8f;

    public Music(Main app) {
        this.app = app;
        enabled = app.getAudioRenderer() != null;
        if (!enabled) return;
        Thread t = new Thread(() -> {
            LinkedHashMap<String, Supplier<byte[]>> gen = new LinkedHashMap<>();
            gen.put("spell", MusicSynth::spell);
            gen.put("chime", MusicSynth::chime);
            gen.put("wind", MusicSynth::wind);
            gen.put("birds", MusicSynth::birds);
            gen.put("crickets", MusicSynth::crickets);
            gen.put("rain", MusicSynth::rain);
            for (Map.Entry<String, Supplier<byte[]>> e : gen.entrySet()) {
                try {
                    ready.add(new Object[]{e.getKey(), e.getValue().get()});
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            }
        }, "music-synth");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private AudioNode node(String name, byte[] pcm, boolean loop) {
        AudioBuffer buf = new AudioBuffer();
        buf.setupFormat(2, 16, MusicSynth.RATE);
        ByteBuffer bb = BufferUtils.createByteBuffer(pcm.length);
        bb.put(pcm).flip();
        buf.updateData(bb);
        AudioNode n = new AudioNode(buf, new AudioKey(name));
        n.setPositional(false);
        n.setLooping(loop);
        n.setVolume(0);
        return n;
    }

    public void sfx(String name) {
        AudioNode n = tracks.get(name);
        if (n != null) {
            n.setVolume(0.5f * masterVolume);
            n.playInstance();
        }
    }

    public void update(float dt, GamePanel core, World3D world) {
        if (!enabled) return;
        Object[] r;
        while ((r = ready.poll()) != null) {
            String name = (String) r[0];
            boolean loop = !name.equals("spell") && !name.equals("chime");
            AudioNode n = node(name, (byte[]) r[1], loop);
            tracks.put(name, n);
            vol.put(name, 0f);
            if (loop) n.play();
        }
        Scene s = core.scene();
        String mood;
        if (s instanceof CafeScene) mood = "cafe";
        else if (s instanceof HouseScene hs) mood = hs.evil() ? "dark" : "cafe";
        else if (s instanceof HomeScene) mood = "cafe";
        else if (s instanceof MiniGame) mood = "drama";
        else if (s instanceof WorldScene ws) {
            if (ws.racing() || ws.danger()) mood = "drama";
            else if (world.zoneDark() > 0.45f) mood = "dark";
            else if (world.dayNight.night > 0.55f) mood = "night";
            else mood = "day";
        } else if (s instanceof EndingScene) mood = "epic";
        else mood = "title";
        orchestra(dt, mood);

        boolean outdoors = s instanceof WorldScene;
        boolean cafe = s instanceof CafeScene;
        float rain = world.weather.rain;
        for (Map.Entry<String, AudioNode> e : tracks.entrySet()) {
            String k = e.getKey();
            if (k.equals("spell") || k.equals("chime")) continue;
            float target = 0;
            switch (k) {
                case "rain" -> target = (outdoors ? 0.55f : cafe ? 0.25f : 0) * rain;
                case "wind" -> target = outdoors ? 0.18f + world.zoneDark() * 0.2f + rain * 0.15f : cafe ? 0.06f : 0;
                case "birds" -> target = outdoors ? 0.35f * (1 - world.dayNight.night) * (1 - rain) * (1 - world.zoneDark()) : 0;
                case "crickets" -> target = outdoors ? 0.3f * world.dayNight.night * (1 - rain) : 0;
                default -> target = 0;
            }
            float v = vol.getOrDefault(k, 0f);
            v += (target - v) * Math.min(1, dt * 0.45f);
            vol.put(k, v);
            e.getValue().setVolume(v * masterVolume);
        }
    }

    // ================================================================ Orchester (echte Aufnahmen)

    /** Stücke je Stimmung (assets/music, Kevin MacLeod – incompetech.com, CC BY 4.0). */
    private static final Map<String, String[]> PLAYLISTS = Map.of(
        "title", new String[]{"title", "epic"},
        "epic", new String[]{"epic", "title"},
        "day", new String[]{"day1", "day2"},
        "night", new String[]{"night1", "night2"},
        "dark", new String[]{"dark1", "dark2"},
        "drama", new String[]{"drama1", "drama2", "drama3"},
        "cafe", new String[]{"cafe1", "cafe2"});
    public static float musicVolume = 0.7f;
    private AudioNode current, fading;
    private String currentMood = "";
    private float currentVol, fadingVol, moodHold;
    private final Map<String, Integer> nextIndex = new HashMap<>();
    private final Random rnd = new Random();

    private void orchestra(float dt, String mood) {
        // kurze Stimmungswechsel (z. B. ein Wolf huscht vorbei) nicht sofort umschalten
        if (!mood.equals(currentMood)) {
            moodHold += dt;
            boolean urgent = mood.equals("drama") || currentMood.isEmpty();
            if (moodHold > (urgent ? 0.6f : 4f)) {
                moodHold = 0;
                currentMood = mood;
                startTrack(mood);
            }
        } else {
            moodHold = 0;
        }
        // Ende eines Stücks: das nächste derselben Stimmung
        if (current != null && current.getStatus() == com.jme3.audio.AudioSource.Status.Stopped && currentVol > 0.05f) startTrack(currentMood);
        float target = musicVolume * masterVolume * (currentMood.equals("drama") ? 0.95f : currentMood.equals("cafe") ? 0.75f : 0.85f);
        currentVol = Math.min(target, currentVol + dt * 0.35f);
        if (currentVol > target) currentVol = target;
        if (current != null) current.setVolume(currentVol);
        if (fading != null) {
            fadingVol -= dt * 0.4f;
            if (fadingVol <= 0) {
                fading.stop();
                app.getRootNode().detachChild(fading);
                fading = null;
            } else fading.setVolume(fadingVol);
        }
    }

    private void startTrack(String mood) {
        String[] list = PLAYLISTS.get(mood);
        if (list == null) return;
        int i = nextIndex.getOrDefault(mood, rnd.nextInt(list.length));
        nextIndex.put(mood, (i + 1) % list.length);
        if (fading != null) {
            fading.stop();
            app.getRootNode().detachChild(fading);
        }
        fading = current;
        fadingVol = currentVol;
        try {
            AudioNode n = new AudioNode(app.getAssetManager(), "music/" + list[i] + ".ogg", AudioData.DataType.Stream);
            n.setPositional(false);
            n.setLooping(false);
            n.setVolume(0);
            app.getRootNode().attachChild(n);
            n.play();
            if (Boolean.getBoolean("dw.musiclog")) System.out.println("Musik: " + mood + " -> " + list[i]);
            current = n;
            currentVol = 0;
        } catch (Exception e) {
            System.err.println("Musik nicht ladbar: " + e.getMessage());
            current = null;
        }
    }
}
