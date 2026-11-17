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
            gen.put("night", MusicSynth::night);
            gen.put("spell", MusicSynth::spell);
            gen.put("chime", MusicSynth::chime);
            gen.put("day", MusicSynth::day);
            gen.put("wind", MusicSynth::wind);
            gen.put("birds", MusicSynth::birds);
            gen.put("crickets", MusicSynth::crickets);
            gen.put("rain", MusicSynth::rain);
            gen.put("cafe", MusicSynth::cafe);
            gen.put("dark", MusicSynth::dark);
            gen.put("drama", MusicSynth::drama);
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
        else if (s instanceof MiniGame) mood = "drama";
        else if (s instanceof WorldScene ws) {
            if (ws.racing()) mood = "drama";
            else if (world.zoneDark() > 0.45f) mood = "dark";
            else if (world.dayNight.night > 0.55f) mood = "night";
            else mood = "day";
        } else if (s instanceof EndingScene) mood = "night";
        else mood = "night";
        if (!tracks.containsKey(mood)) mood = tracks.containsKey("night") ? "night" : null;

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
                default -> target = k.equals(mood) ? 0.55f : 0;
            }
            float v = vol.getOrDefault(k, 0f);
            v += (target - v) * Math.min(1, dt * 0.45f);
            vol.put(k, v);
            e.getValue().setVolume(v * masterVolume);
        }
    }
}
