package daemmerwald;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entflechtet verschachtelte Vertex-Daten in einer GLB-Datei (jMonkeyEngine liest z. B. Knochen-Indizes
 * als Bytes in verschachtelten Puffern falsch – die Figur würde zersplittern).
 */
public final class GlbFix {
    private GlbFix() { }

    public static void deinterleave(Path in, Path out) throws Exception {
        ByteBuffer d = ByteBuffer.wrap(Files.readAllBytes(in)).order(ByteOrder.LITTLE_ENDIAN);
        int jsonLen = d.getInt(12);
        String json = new String(d.array(), 20, jsonLen, StandardCharsets.UTF_8);
        JsonObject j = JsonParser.parseString(json).getAsJsonObject();
        int binStart = 20 + jsonLen;
        int binLen = d.getInt(binStart);
        byte[] bin = new byte[binLen];
        System.arraycopy(d.array(), binStart + 8, bin, 0, binLen);
        ByteArrayOutputStream outBin = new ByteArrayOutputStream();
        outBin.write(bin);
        JsonArray views = j.getAsJsonArray("bufferViews");
        for (var e : j.getAsJsonArray("accessors")) {
            JsonObject a = e.getAsJsonObject();
            if (!a.has("bufferView")) continue;
            JsonObject bv = views.get(a.get("bufferView").getAsInt()).getAsJsonObject();
            int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : 0;
            int es = compSize(a.get("componentType").getAsInt()) * numComp(a.get("type").getAsString());
            if (stride == 0 || stride == es) continue;
            int base = (bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0) + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
            while (outBin.size() % 4 != 0) outBin.write(0);
            int off = outBin.size();
            int count = a.get("count").getAsInt();
            for (int i = 0; i < count; i++) outBin.write(bin, base + i * stride, es);
            JsonObject nv = new JsonObject();
            nv.addProperty("buffer", 0);
            nv.addProperty("byteOffset", off);
            nv.addProperty("byteLength", es * count);
            views.add(nv);
            a.addProperty("bufferView", views.size() - 1);
            a.addProperty("byteOffset", 0);
        }
        while (outBin.size() % 4 != 0) outBin.write(0);
        j.getAsJsonArray("buffers").get(0).getAsJsonObject().addProperty("byteLength", outBin.size());
        byte[] js = j.toString().getBytes(StandardCharsets.UTF_8);
        int jsPad = (4 - js.length % 4) % 4;
        byte[] b = outBin.toByteArray();
        ByteBuffer o = ByteBuffer.allocate(12 + 8 + js.length + jsPad + 8 + b.length).order(ByteOrder.LITTLE_ENDIAN);
        o.putInt(0x46546C67).putInt(2).putInt(o.capacity());
        o.putInt(js.length + jsPad).putInt(0x4E4F534A).put(js);
        for (int i = 0; i < jsPad; i++) o.put((byte) ' ');
        o.putInt(b.length).putInt(0x004E4942).put(b);
        Files.write(out, o.array());
    }

    private static int compSize(int t) {
        return switch (t) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            default -> 4;
        };
    }

    private static int numComp(String t) {
        return switch (t) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT4" -> 16;
            default -> 1;
        };
    }
}
