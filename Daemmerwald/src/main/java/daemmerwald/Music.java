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
        installVoice();
        Thread t = new Thread(() -> {
            LinkedHashMap<String, Supplier<byte[]>> gen = new LinkedHashMap<>();
            gen.put("spell", MusicSynth::spell);
            gen.put("chime", MusicSynth::chime);
            gen.put("wind", MusicSynth::wind);
            gen.put("crickets", MusicSynth::crickets);
            gen.put("rain", MusicSynth::rain);
            gen.put("step", MusicSynth::step);
            gen.put("door", MusicSynth::door);
            gen.put("owl", MusicSynth::owl);
            gen.put("hit", MusicSynth::hit);
            gen.put("roar", MusicSynth::roar);
            gen.put("whoosh", MusicSynth::whoosh);
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

    private static final Set<String> SFX = Set.of("spell", "chime", "step", "door", "owl", "hit", "roar", "whoosh");

    // ---------------------------------------------------------------- Sprachausgabe

    private AudioNode voiceNode;

    private void installVoice() {
        Voice.player = new Voice.Player() {
            @Override
            public void play(java.util.List<String> assets, float pitch) {
                stop();
                voiceQueue.addAll(assets);
                voicePitch = pitch;
                next();
            }

            private void next() {
                if (voiceQueue.isEmpty()) return;
                String asset = voiceQueue.remove(0);
                float pitch = voicePitch;
                try {
                    AudioKey key = new AudioKey(asset, true, false);
                    AudioNode n = new AudioNode((AudioData) app.getAssetManager().loadAsset(key), key);
                    n.setPositional(false);
                    n.setLooping(false);
                    n.setVolume(1.15f * masterVolume * Voice.volume);
                    n.setPitch(pitch);
                    app.getRootNode().attachChild(n);
                    n.play();
                    voiceNode = n;
                } catch (Throwable e) {
                    GamePanel.log("Sprachdatei nicht abspielbar: " + asset + " " + e);
                    voiceNode = null;
                }
            }

            @Override
            public void stop() {
                voiceQueue.clear();
                if (voiceNode != null) {
                    voiceNode.stop();
                    app.getRootNode().detachChild(voiceNode);
                    voiceNode = null;
                }
            }
        };
    }

    private final java.util.List<String> voiceQueue = new java.util.ArrayList<>();
    private float voicePitch = 1;

    /** Nächstes Stück eines zusammengesetzten Satzes abspielen, wenn das vorige fertig ist. */
    private void voiceTick() {
        if (voiceNode != null && voiceNode.getStatus() == com.jme3.audio.AudioSource.Status.Stopped) {
            app.getRootNode().detachChild(voiceNode);
            voiceNode = null;
            if (!voiceQueue.isEmpty()) {
                java.util.List<String> rest = new java.util.ArrayList<>(voiceQueue);
                Voice.player.play(rest, voicePitch);
            }
        }
    }

    private boolean speaking() { return (voiceNode != null && voiceNode.getStatus() == com.jme3.audio.AudioSource.Status.Playing) || !voiceQueue.isEmpty(); }

    public void sfx(String name) { sfx(name, 1f); }

    /** Leiser Effekt (Schritte). */
    public void sfx(String name, float pitch, float volume) {
        AudioNode n = tracks.get(name);
        if (n != null) {
            n.setVolume(volume * masterVolume);
            n.setPitch(Math.max(0.5f, Math.min(2f, pitch)));
            n.playInstance();
        }
    }

    /** Soundeffekt mit Tonhöhe (jeder Zauber klingt anders). */
    public void sfx(String name, float pitch) {
        AudioNode n = tracks.get(name);
        if (n != null) {
            n.setVolume(0.5f * masterVolume);
            n.setPitch(Math.max(0.5f, Math.min(2f, pitch)));
            n.playInstance();
        }
    }

    public void update(float dt, GamePanel core, World3D world) {
        if (!enabled) return;
        Object[] r;
        while ((r = ready.poll()) != null) {
            String name = (String) r[0];
            boolean loop = !SFX.contains(name);
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
            Palace.Room pr = world.inPalace ? world.palace.roomAt(ws.focus().x, ws.focus().z) : null;
            if (ws.bossFight()) mood = "boss";
            else if (ws.racing() || ws.danger()) mood = "drama";
            else if (pr != null) mood = pr.id().equals("crypt") ? "dark" : pr.id().startsWith("deep") ? "deep" : "palace";
            else if (world.zoneDark() > 0.45f) mood = "dark";
            else if (world.dayNight.night > 0.55f) mood = "night";
            else mood = "day";
        } else if (s instanceof EndingScene) mood = "epic";
        else mood = "title";
        orchestra(dt, mood);
        voiceTick();
        owlT -= dt;
        if (owlT <= 0) {
            owlT = 25 + rnd.nextFloat() * 40;
            if (s instanceof WorldScene && !world.inPalace && world.dayNight.night > 0.6f) sfx("owl", 0.9f + rnd.nextFloat() * 0.2f, 0.25f);
        }

        boolean outdoors = s instanceof WorldScene && !world.inPalace;
        boolean cafe = s instanceof CafeScene;
        float rain = world.weather.rain;
        for (Map.Entry<String, AudioNode> e : tracks.entrySet()) {
            String k = e.getKey();
            if (SFX.contains(k)) continue;
            float target = 0;
            switch (k) {
                case "rain" -> target = (outdoors ? 0.2f : cafe ? 0.1f : 0) * rain;
                case "wind" -> target = outdoors ? 0.18f + world.zoneDark() * 0.2f + rain * 0.15f : cafe ? 0.06f : 0;
                case "birds" -> target = outdoors ? 0.35f * (1 - world.dayNight.night) * (1 - rain) * (1 - world.zoneDark()) : 0;
                case "crickets" -> target = outdoors ? 0.12f * world.dayNight.night * (1 - rain) : 0;
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
        "cafe", new String[]{"cafe1", "cafe2"},
        "palace", new String[]{"day2", "night1", "epic"},
        "boss", new String[]{"boss1", "boss2"},
        "deep", new String[]{"deep1", "dark2"});
    public static float musicVolume = 0.7f;
    private AudioNode current, fading;
    private String currentMood = "";
    private float currentVol, fadingVol, moodHold, trackTime;
    private final Map<String, Integer> nextIndex = new HashMap<>();
    private final Random rnd = new Random();
    private float owlT = 20;

    private void orchestra(float dt, String mood) {
        // kurze Stimmungswechsel (z. B. ein Wolf huscht vorbei) nicht sofort umschalten
        if (!mood.equals(currentMood)) {
            moodHold += dt;
            boolean urgent = mood.equals("boss") || currentMood.isEmpty() || (mood.equals("drama") && !currentMood.equals("boss"));
            // ein Stück nicht nach wenigen Sekunden wieder abwürgen (sonst laufen ständig zwei Stücke übereinander)
            float need = urgent ? 1.2f : trackTime < 20 ? 12f : 5f;
            if (moodHold > need) {
                moodHold = 0;
                currentMood = mood;
                startTrack(mood);
            }
        } else {
            moodHold = 0;
        }
        // Ende eines Stücks: das nächste derselben Stimmung
        trackTime += dt;
        // (erst nach ein paar Sekunden – falls ein Tongerät Stücke sofort beendet, nicht ständig neu starten)
        if (current != null && trackTime > 8 && current.getStatus() == com.jme3.audio.AudioSource.Status.Stopped) startTrack(currentMood);
        float target = musicVolume * masterVolume * (currentMood.equals("drama") ? 0.95f : currentMood.equals("cafe") ? 0.75f : 0.85f) * (speaking() ? 0.5f : 1f);
        currentVol = Math.min(target, currentVol + dt * 0.35f);
        if (currentVol > target) currentVol = Math.max(target, currentVol - dt * 1.5f);
        if (current != null) current.setVolume(currentVol);
        if (fading != null) {
            fadingVol -= dt * 1.2f;
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
            // ohne Stream-Zwischenspeicher: der hängt sich beim Wechseln der Stücke sonst auf
            AudioKey key = new AudioKey("music/" + list[i] + ".ogg", true, false);
            AudioNode n = new AudioNode((AudioData) app.getAssetManager().loadAsset(key), key);
            n.setPositional(false);
            n.setLooping(false);
            n.setVolume(0);
            app.getRootNode().attachChild(n);
            n.play();
            if (Boolean.getBoolean("dw.musiclog")) System.out.println("Musik: " + mood + " -> " + list[i]);
            current = n;
            currentVol = 0;
            trackTime = 0;
        } catch (Exception e) {
            System.err.println("Musik nicht ladbar: " + e.getMessage());
            current = null;
            trackTime = 0;
        }
    }
}
