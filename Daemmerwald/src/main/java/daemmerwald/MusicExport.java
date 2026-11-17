package daemmerwald;

import java.io.*;
import java.nio.file.*;

/** Schreibt die prozedurale Musik als WAV-Dateien (zum Anhören außerhalb des Spiels). */
public class MusicExport {
    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(args.length > 0 ? args[0] : "musik");
        Files.createDirectories(dir);
        write(dir.resolve("1_tag_ruhig.wav"), MusicSynth.day());
        write(dir.resolve("2_nacht_geheimnisvoll.wav"), MusicSynth.night());
        write(dir.resolve("3_dunkle_orte.wav"), MusicSynth.dark());
        write(dir.resolve("4_duell_drama.wav"), MusicSynth.drama());
        write(dir.resolve("5_cafe_harmonisch.wav"), MusicSynth.cafe());
        System.out.println("Musik geschrieben nach " + dir.toAbsolutePath());
    }

    static void write(Path p, byte[] pcm) throws IOException {
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(p)))) {
            int rate = MusicSynth.RATE;
            o.writeBytes("RIFF");
            o.writeInt(Integer.reverseBytes(36 + pcm.length));
            o.writeBytes("WAVEfmt ");
            o.writeInt(Integer.reverseBytes(16));
            o.writeShort(Short.reverseBytes((short) 1));
            o.writeShort(Short.reverseBytes((short) 2));
            o.writeInt(Integer.reverseBytes(rate));
            o.writeInt(Integer.reverseBytes(rate * 4));
            o.writeShort(Short.reverseBytes((short) 4));
            o.writeShort(Short.reverseBytes((short) 16));
            o.writeBytes("data");
            o.writeInt(Integer.reverseBytes(pcm.length));
            o.write(pcm);
        }
    }
}
