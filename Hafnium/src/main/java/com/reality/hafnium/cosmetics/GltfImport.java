// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Turns a glTF model, the kind Sketchfab gives out, into a Hafnium mesh model.
 *
 * It reads the model's triangles and textures, turns it to face the same way
 * as a player, and fits it onto the head or the back at the size picked. All
 * of its textures are packed into one small texture, and very detailed models
 * are simplified until they're small enough to send.
 */
public final class GltfImport {

    public static final int MAX_TRIANGLES = 8000;
    public static final int MAX_CORNERS = 16000;
    private static final int MAX_SOURCE_TRIANGLES = 400_000;
    private static final long MAX_ARCHIVE_BYTES = 120L * 1024 * 1024;

    /** How to fit the model: where it goes, how big, whether a hat sits on top or around the head, and quarter turns. */
    public record Fit(String slot, int size, boolean around, int turns) {
        public Fit next(String what) {
            return switch (what) {
                case "slot" -> new Fit("head".equals(slot) ? "back" : "head", size, around, turns);
                case "size" -> new Fit(slot, (size + 1) % 3, around, turns);
                case "around" -> new Fit(slot, size, !around, turns);
                default -> new Fit(slot, size, around, (turns + 1) % 4);
            };
        }
    }

    private GltfImport() { }

    // --- Reading the files ---------------------------------------------------------------------------

    /** A .glb file, a .gltf file next to its other files, or a .zip holding a .gltf. */
    public static Model3d fromFile(Path file, Fit fit) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        byte[] bytes = Files.readAllBytes(file);
        if (name.endsWith(".zip")) return fromZip(bytes, fit);
        if (name.endsWith(".glb")) return fromGlb(bytes, fit);
        Path dir = file.toAbsolutePath().getParent();
        return convert(parse(bytes), null, uri -> Files.readAllBytes(dir.resolve(uri).normalize()), fit);
    }

    /** The zip archive Sketchfab's download gives, with scene.gltf, scene.bin and a textures folder. */
    public static Model3d fromZip(byte[] zip, Fit fit) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                byte[] data = in.readNBytes((int) Math.min(MAX_ARCHIVE_BYTES - total + 1, Integer.MAX_VALUE - 8));
                total += data.length;
                if (total > MAX_ARCHIVE_BYTES) throw new IOException("That model's files are too big.");
                files.put(e.getName().replace('\\', '/'), data);
            }
        }
        String gltf = files.keySet().stream().filter(n -> n.toLowerCase().endsWith(".gltf")).sorted().findFirst().orElse(null);
        if (gltf == null) {
            String glb = files.keySet().stream().filter(n -> n.toLowerCase().endsWith(".glb")).findFirst().orElse(null);
            if (glb == null) throw new IOException("There's no glTF model in that archive.");
            return fromGlb(files.get(glb), fit);
        }
        String base = gltf.contains("/") ? gltf.substring(0, gltf.lastIndexOf('/') + 1) : "";
        return convert(parse(files.get(gltf)), null, uri -> {
            byte[] f = files.get(normalize(base + uri));
            if (f == null) throw new IOException("The model is missing a file: " + uri);
            return f;
        }, fit);
    }

    public static Model3d fromGlb(byte[] glb, Fit fit) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        if (glb.length < 20 || b.getInt(0) != 0x46546C67) throw new IOException("That isn't a .glb model.");
        int at = 12;
        JsonObject json = null;
        ByteBuffer bin = null;
        while (at + 8 <= glb.length) {
            int len = b.getInt(at), type = b.getInt(at + 4);
            if (len < 0 || at + 8 + len > glb.length) throw new IOException("That .glb model is broken.");
            if (type == 0x4E4F534A) json = parse(java.util.Arrays.copyOfRange(glb, at + 8, at + 8 + len));
            else if (type == 0x004E4942) bin = ByteBuffer.wrap(glb, at + 8, len).slice().order(ByteOrder.LITTLE_ENDIAN);
            at += 8 + len;
        }
        if (json == null) throw new IOException("That .glb model is broken.");
        return convert(json, bin, uri -> { throw new IOException("The model is missing a file: " + uri); }, fit);
    }

    private static JsonObject parse(byte[] bytes) throws IOException {
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("That model file couldn't be read.");
        }
    }

    private static String normalize(String path) {
        String p = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
        List<String> parts = new ArrayList<>();
        for (String s : p.split("/")) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) { if (!parts.isEmpty()) parts.remove(parts.size() - 1); continue; }
            parts.add(s);
        }
        return String.join("/", parts);
    }

    private interface Files2 { byte[] read(String uri) throws IOException; }

    // --- Turning it into triangles ------------------------------------------------------------------

    /** One triangle: three corners in Blockbench's space, their texture spots, and its material. */
    private record Tri(float[] p, float[] uv, int material) { }

    private static final class Reader {
        final JsonObject json;
        final ByteBuffer glbBin;
        final Files2 files;
        final Map<Integer, ByteBuffer> buffers = new HashMap<>();

        Reader(JsonObject json, ByteBuffer glbBin, Files2 files) {
            this.json = json;
            this.glbBin = glbBin;
            this.files = files;
        }

        JsonArray list(String name) {
            return json.has(name) ? json.getAsJsonArray(name) : new JsonArray();
        }

        ByteBuffer buffer(int i) throws IOException {
            ByteBuffer b = buffers.get(i);
            if (b != null) return b;
            JsonObject o = list("buffers").get(i).getAsJsonObject();
            if (!o.has("uri")) {
                if (glbBin == null) throw new IOException("The model is missing its data.");
                b = glbBin;
            } else {
                b = ByteBuffer.wrap(bytesOf(o.get("uri").getAsString())).order(ByteOrder.LITTLE_ENDIAN);
            }
            buffers.put(i, b);
            return b;
        }

        byte[] bytesOf(String uri) throws IOException {
            if (uri.startsWith("data:")) {
                int comma = uri.indexOf(',');
                if (comma < 0 || !uri.substring(0, comma).endsWith(";base64")) throw new IOException("The model has data it can't read.");
                return Base64.getDecoder().decode(uri.substring(comma + 1));
            }
            return files.read(uri);
        }

        byte[] viewBytes(int view) throws IOException {
            JsonObject v = list("bufferViews").get(view).getAsJsonObject();
            ByteBuffer b = buffer(v.get("buffer").getAsInt());
            int off = v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0, len = v.get("byteLength").getAsInt();
            byte[] out = new byte[len];
            b.get(off, out);
            return out;
        }

        /** An accessor's values as floats, `components` per item, with normalized whole numbers turned into 0 to 1. */
        float[] floats(int accessor) throws IOException {
            JsonObject a = list("accessors").get(accessor).getAsJsonObject();
            int count = a.get("count").getAsInt();
            int comps = switch (a.get("type").getAsString()) {
                case "SCALAR" -> 1; case "VEC2" -> 2; case "VEC3" -> 3; case "VEC4" -> 4; default -> throw new IOException("The model has data it can't read.");
            };
            int type = a.get("componentType").getAsInt();
            boolean norm = a.has("normalized") && a.get("normalized").getAsBoolean();
            float[] out = new float[count * comps];
            if (!a.has("bufferView")) return out;
            JsonObject v = list("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
            ByteBuffer b = buffer(v.get("buffer").getAsInt());
            int size = switch (type) { case 5120, 5121 -> 1; case 5122, 5123 -> 2; default -> 4; };
            int stride = v.has("byteStride") ? v.get("byteStride").getAsInt() : size * comps;
            int base = (v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0) + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
            if ((long) base + (long) stride * Math.max(0, count - 1) + (long) size * comps > b.limit()) throw new IOException("The model's data is broken.");
            for (int i = 0; i < count; i++) {
                for (int c = 0; c < comps; c++) {
                    int at = base + i * stride + c * size;
                    float f = switch (type) {
                        case 5126 -> b.getFloat(at);
                        case 5121 -> norm ? (b.get(at) & 0xFF) / 255f : b.get(at) & 0xFF;
                        case 5120 -> norm ? Math.max(b.get(at) / 127f, -1) : b.get(at);
                        case 5123 -> norm ? (b.getShort(at) & 0xFFFF) / 65535f : b.getShort(at) & 0xFFFF;
                        case 5122 -> norm ? Math.max(b.getShort(at) / 32767f, -1) : b.getShort(at);
                        case 5125 -> (float) (b.getInt(at) & 0xFFFFFFFFL);
                        default -> throw new IOException("The model has data it can't read.");
                    };
                    out[i * comps + c] = f;
                }
            }
            return out;
        }

        int[] indices(int accessor) throws IOException {
            float[] f = floats(accessor);
            int[] out = new int[f.length];
            for (int i = 0; i < f.length; i++) out[i] = (int) f[i];
            return out;
        }
    }

    private static Model3d convert(JsonObject json, ByteBuffer glbBin, Files2 files, Fit fit) throws IOException {
        Reader r = new Reader(json, glbBin, files);
        List<Tri> tris = new ArrayList<>();
        JsonArray scenes = r.list("scenes");
        JsonArray roots;
        if (scenes.isEmpty()) {
            roots = new JsonArray();
            for (int i = 0; i < r.list("nodes").size(); i++) roots.add(i);
        } else {
            int scene = json.has("scene") ? json.get("scene").getAsInt() : 0;
            JsonObject s = scenes.get(Math.min(scene, scenes.size() - 1)).getAsJsonObject();
            roots = s.has("nodes") ? s.getAsJsonArray("nodes") : new JsonArray();
        }
        for (JsonElement n : roots) walk(r, n.getAsInt(), new Matrix4f(), tris, 0);
        if (tris.isEmpty()) throw new IOException("The model has no triangles to show.");
        return build(r, tris, fit);
    }

    private static void walk(Reader r, int index, Matrix4f parent, List<Tri> out, int depth) throws IOException {
        if (depth > 64) throw new IOException("The model is nested too deeply.");
        JsonObject node = r.list("nodes").get(index).getAsJsonObject();
        Matrix4f m = new Matrix4f(parent).mul(local(node));
        if (node.has("mesh")) {
            JsonObject mesh = r.list("meshes").get(node.get("mesh").getAsInt()).getAsJsonObject();
            for (JsonElement pe : mesh.getAsJsonArray("primitives")) addPrimitive(r, pe.getAsJsonObject(), m, out);
        }
        if (node.has("children")) for (JsonElement c : node.getAsJsonArray("children")) walk(r, c.getAsInt(), m, out, depth + 1);
    }

    private static Matrix4f local(JsonObject node) {
        if (node.has("matrix")) {
            JsonArray a = node.getAsJsonArray("matrix");
            float[] v = new float[16];
            for (int i = 0; i < 16; i++) v[i] = a.get(i).getAsFloat();
            return new Matrix4f().set(v);
        }
        Matrix4f m = new Matrix4f();
        if (node.has("translation")) { JsonArray t = node.getAsJsonArray("translation"); m.translate(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat()); }
        if (node.has("rotation")) { JsonArray q = node.getAsJsonArray("rotation"); m.rotate(new Quaternionf(q.get(0).getAsFloat(), q.get(1).getAsFloat(), q.get(2).getAsFloat(), q.get(3).getAsFloat())); }
        if (node.has("scale")) { JsonArray s = node.getAsJsonArray("scale"); m.scale(s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat()); }
        return m;
    }

    private static void addPrimitive(Reader r, JsonObject prim, Matrix4f m, List<Tri> out) throws IOException {
        int mode = prim.has("mode") ? prim.get("mode").getAsInt() : 4;
        if (mode < 4) return; // points and lines have nothing to show
        JsonObject attrs = prim.getAsJsonObject("attributes");
        if (!attrs.has("POSITION")) return;
        int material = prim.has("material") ? prim.get("material").getAsInt() : -1;
        String uvName = "TEXCOORD_" + texCoordOf(r, material);
        float[] pos = r.floats(attrs.get("POSITION").getAsInt());
        float[] uv = attrs.has(uvName) ? r.floats(attrs.get(uvName).getAsInt()) : null;
        int count = pos.length / 3;
        int[] idx;
        if (prim.has("indices")) idx = r.indices(prim.get("indices").getAsInt());
        else { idx = new int[count]; for (int i = 0; i < count; i++) idx[i] = i; }
        List<int[]> corners = new ArrayList<>();
        if (mode == 4) for (int i = 0; i + 2 < idx.length; i += 3) corners.add(new int[]{idx[i], idx[i + 1], idx[i + 2]});
        else if (mode == 5) for (int i = 0; i + 2 < idx.length; i++) corners.add(i % 2 == 0 ? new int[]{idx[i], idx[i + 1], idx[i + 2]} : new int[]{idx[i + 1], idx[i], idx[i + 2]});
        else for (int i = 1; i + 1 < idx.length; i++) corners.add(new int[]{idx[0], idx[i], idx[i + 1]});
        if (out.size() + corners.size() > MAX_SOURCE_TRIANGLES) throw new IOException("That model is far too detailed to wear. Try one with fewer faces.");
        Vector3f v = new Vector3f();
        for (int[] c : corners) {
            float[] p = new float[9], t = new float[6];
            for (int k = 0; k < 3; k++) {
                int i = c[k];
                if (i < 0 || i >= count) throw new IOException("The model's data is broken.");
                m.transformPosition(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], v);
                // glTF models face +Z with +X on their left. Blockbench's player faces -Z with +X on their right: half a turn around the up line.
                p[k * 3] = -v.x;
                p[k * 3 + 1] = v.y;
                p[k * 3 + 2] = -v.z;
                if (uv != null) { t[k * 2] = uv[i * 2]; t[k * 2 + 1] = uv[i * 2 + 1]; }
                else { t[k * 2] = 0.5f; t[k * 2 + 1] = 0.5f; }
            }
            out.add(new Tri(p, t, material));
        }
    }

    private static int texCoordOf(Reader r, int material) {
        JsonObject tex = baseTexture(r, material);
        return tex != null && tex.has("texCoord") ? tex.get("texCoord").getAsInt() : 0;
    }

    private static JsonObject baseTexture(Reader r, int material) {
        if (material < 0 || material >= r.list("materials").size()) return null;
        JsonObject mat = r.list("materials").get(material).getAsJsonObject();
        if (mat.has("pbrMetallicRoughness") && mat.getAsJsonObject("pbrMetallicRoughness").has("baseColorTexture")) {
            return mat.getAsJsonObject("pbrMetallicRoughness").getAsJsonObject("baseColorTexture");
        }
        if (mat.has("extensions") && mat.getAsJsonObject("extensions").has("KHR_materials_pbrSpecularGlossiness")) {
            JsonObject sg = mat.getAsJsonObject("extensions").getAsJsonObject("KHR_materials_pbrSpecularGlossiness");
            if (sg.has("diffuseTexture")) return sg.getAsJsonObject("diffuseTexture");
        }
        return null;
    }

    private static float[] baseColor(Reader r, int material) {
        if (material < 0 || material >= r.list("materials").size()) return new float[]{0.8f, 0.8f, 0.8f, 1};
        JsonObject mat = r.list("materials").get(material).getAsJsonObject();
        JsonArray f = null;
        if (mat.has("pbrMetallicRoughness") && mat.getAsJsonObject("pbrMetallicRoughness").has("baseColorFactor")) {
            f = mat.getAsJsonObject("pbrMetallicRoughness").getAsJsonArray("baseColorFactor");
        } else if (mat.has("extensions") && mat.getAsJsonObject("extensions").has("KHR_materials_pbrSpecularGlossiness")) {
            JsonObject sg = mat.getAsJsonObject("extensions").getAsJsonObject("KHR_materials_pbrSpecularGlossiness");
            if (sg.has("diffuseFactor")) f = sg.getAsJsonArray("diffuseFactor");
        }
        if (f == null || f.size() < 4) return new float[]{1, 1, 1, 1};
        return new float[]{f.get(0).getAsFloat(), f.get(1).getAsFloat(), f.get(2).getAsFloat(), f.get(3).getAsFloat()};
    }

    private static BufferedImage imageOf(Reader r, int material) {
        JsonObject tex = baseTexture(r, material);
        if (tex == null) return null;
        try {
            JsonObject t = r.list("textures").get(tex.get("index").getAsInt()).getAsJsonObject();
            if (!t.has("source")) return null;
            JsonObject img = r.list("images").get(t.get("source").getAsInt()).getAsJsonObject();
            byte[] bytes = img.has("uri") ? r.bytesOf(img.get("uri").getAsString()) : r.viewBytes(img.get("bufferView").getAsInt());
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null; // a texture format Java can't read, like WebP: the material's plain colour is used instead
        }
    }

    // --- Fitting it onto the player -----------------------------------------------------------------

    private static Model3d build(Reader r, List<Tri> tris, Fit fit) throws IOException {
        // Quarter turns around the up line, if the player asked for them.
        for (Tri t : tris) {
            for (int k = 0; k < 3; k++) {
                for (int n = 0; n < fit.turns(); n++) {
                    float x = t.p[k * 3], z = t.p[k * 3 + 2];
                    t.p[k * 3] = -z;
                    t.p[k * 3 + 2] = x;
                }
            }
        }
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Tri t : tris) for (int k = 0; k < 9; k++) {
            min[k % 3] = Math.min(min[k % 3], t.p[k]);
            max[k % 3] = Math.max(max[k % 3], t.p[k]);
        }
        float sx = max[0] - min[0], sy = max[1] - min[1], sz = max[2] - min[2];
        if (!(sx + sy + sz > 0) || !Float.isFinite(sx + sy + sz)) throw new IOException("The model has no size.");
        boolean head = "head".equals(fit.slot());
        float target = head ? new float[]{7, 10, 14}[fit.size()] : new float[]{8, 12, 16}[fit.size()];
        float scale = head ? target / Math.max(Math.max(sx, sz), sy * 0.75f) : target / Math.max(Math.max(sx, sy), sz * 0.75f);
        float cx = (min[0] + max[0]) / 2, cy = (min[1] + max[1]) / 2, cz = (min[2] + max[2]) / 2;
        for (Tri t : tris) for (int k = 0; k < 3; k++) {
            float x = (t.p[k * 3] - cx) * scale, y = (t.p[k * 3 + 1] - cy) * scale, z = (t.p[k * 3 + 2] - cz) * scale;
            if (head) {
                // Centred on the head. On top: its bottom sits just below the top of the head. Around: centred on the head.
                y += fit.around() ? 28 : 31 + sy * scale / 2;
            } else {
                // Centred on the back, with its front touching the back of the body.
                y += 18;
                z += 2 + sz * scale / 2;
            }
            t.p[k * 3] = x;
            t.p[k * 3 + 1] = y;
            t.p[k * 3 + 2] = z;
        }

        // One texture for everything: each material gets its own square of it.
        Map<Integer, Integer> cellOf = new LinkedHashMap<>();
        for (Tri t : tris) cellOf.putIfAbsent(t.material, cellOf.size());
        for (int side : new int[]{256, 128, 64}) {
            Model3d model = pack(r, tris, cellOf, side);
            if (model.pack().length <= 250 * 1024) return model;
        }
        throw new IOException("That model is too big to send, even simplified.");
    }

    private static Model3d pack(Reader r, List<Tri> tris, Map<Integer, Integer> cellOf, int side) throws IOException {
        int cells = cellOf.size();
        int cols = (int) Math.ceil(Math.sqrt(cells)), rows = (int) Math.ceil(cells / (double) cols);
        int cw = Math.max(1, side / cols), ch = Math.max(1, side / rows);
        BufferedImage atlas = new BufferedImage(cw * cols, ch * rows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (var e : cellOf.entrySet()) {
            int mat = e.getKey(), cell = e.getValue();
            int x0 = (cell % cols) * cw, y0 = (cell / cols) * ch;
            float[] tint = baseColor(r, mat);
            String alphaMode = "OPAQUE";
            float cutoff = 0.5f;
            if (mat >= 0 && mat < r.list("materials").size()) {
                JsonObject m = r.list("materials").get(mat).getAsJsonObject();
                if (m.has("alphaMode")) alphaMode = m.get("alphaMode").getAsString();
                if (m.has("alphaCutoff")) cutoff = m.get("alphaCutoff").getAsFloat();
            }
            BufferedImage img = imageOf(r, mat);
            if (img != null) g.drawImage(img, x0, y0, cw, ch, null);
            for (int y = y0; y < y0 + ch; y++) for (int x = x0; x < x0 + cw; x++) {
                int argb = img != null ? atlas.getRGB(x, y) : 0xFFFFFFFF;
                float a = (argb >>> 24) / 255f * tint[3];
                int rr = Math.round(((argb >> 16) & 0xFF) * clamp(tint[0])), gg = Math.round(((argb >> 8) & 0xFF) * clamp(tint[1])),
                        bb = Math.round((argb & 0xFF) * clamp(tint[2]));
                if (alphaMode.equals("OPAQUE")) a = 1;
                else if (alphaMode.equals("MASK")) a = a >= cutoff ? 1 : 0;
                atlas.setRGB(x, y, (Math.round(clamp(a) * 255) << 24) | (rr << 16) | (gg << 8) | bb);
            }
        }
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(atlas, "png", png);

        // Each corner's spot on the new texture. A texture that repeats is brought back into its square.
        int w = atlas.getWidth(), h = atlas.getHeight();
        List<float[]> uvs = new ArrayList<>(tris.size());
        for (Tri t : tris) {
            int cell = cellOf.get(t.material);
            float x0 = (cell % cols) * cw, y0 = (cell / cols) * ch;
            float shiftU = (float) Math.floor(Math.min(t.uv[0], Math.min(t.uv[2], t.uv[4])));
            float shiftV = (float) Math.floor(Math.min(t.uv[1], Math.min(t.uv[3], t.uv[5])));
            float[] out = new float[6];
            for (int k = 0; k < 3; k++) {
                float u = clamp(t.uv[k * 2] - shiftU), v = clamp(t.uv[k * 2 + 1] - shiftV);
                out[k * 2] = (x0 + 0.5f + u * (cw - 1)) / w;
                out[k * 2 + 1] = (y0 + 0.5f + v * (ch - 1)) / h;
            }
            uvs.add(out);
        }

        // Join corners that land in the same spot. If there are still too many triangles, use a coarser grid,
        // which merges nearby corners and drops triangles too small to see.
        for (float grid = 1 / 16f; grid <= 2f; grid *= 2) {
            Map<Long, Integer> cluster = new HashMap<>();
            List<float[]> sums = new ArrayList<>();
            Map<String, Integer> corner = new HashMap<>();
            List<int[]> cornerInfo = new ArrayList<>(); // cluster, u, v as 16-bit
            List<Integer> out = new ArrayList<>();
            boolean tooMany = false;
            for (int i = 0; i < tris.size() && !tooMany; i++) {
                Tri t = tris.get(i);
                int[] cl = new int[3], ids = new int[3];
                for (int k = 0; k < 3; k++) {
                    long key = key(Math.round(t.p[k * 3] / grid), Math.round(t.p[k * 3 + 1] / grid), Math.round(t.p[k * 3 + 2] / grid));
                    Integer c = cluster.get(key);
                    if (c == null) {
                        c = sums.size();
                        cluster.put(key, c);
                        sums.add(new float[4]);
                    }
                    float[] s = sums.get(c);
                    s[0] += t.p[k * 3]; s[1] += t.p[k * 3 + 1]; s[2] += t.p[k * 3 + 2]; s[3]++;
                    cl[k] = c;
                }
                if (cl[0] == cl[1] || cl[1] == cl[2] || cl[0] == cl[2]) continue;
                float[] uv = uvs.get(i);
                for (int k = 0; k < 3; k++) {
                    int u = Math.round(uv[k * 2] * 65535), v = Math.round(uv[k * 2 + 1] * 65535);
                    String ck = cl[k] + ":" + u + ":" + v;
                    Integer id = corner.get(ck);
                    if (id == null) {
                        id = cornerInfo.size();
                        corner.put(ck, id);
                        cornerInfo.add(new int[]{cl[k], u, v});
                    }
                    ids[k] = id;
                }
                out.add(ids[0]); out.add(ids[1]); out.add(ids[2]);
                if (out.size() / 3 > MAX_TRIANGLES || cornerInfo.size() > MAX_CORNERS) tooMany = true;
            }
            if (tooMany || out.isEmpty()) continue;
            float[] pos = new float[cornerInfo.size() * 3], uv = new float[cornerInfo.size() * 2];
            for (int i = 0; i < cornerInfo.size(); i++) {
                int[] c = cornerInfo.get(i);
                float[] s = sums.get(c[0]);
                for (int a = 0; a < 3; a++) pos[i * 3 + a] = Math.max(-47.9f, Math.min(79.9f, s[a] / s[3]));
                uv[i * 2] = c[1] / 65535f;
                uv[i * 2 + 1] = c[2] / 65535f;
            }
            int[] idx = out.stream().mapToInt(Integer::intValue).toArray();
            return new Model3d(new ArrayList<>(), w, h, png.toByteArray(), pos, uv, idx);
        }
        throw new IOException("That model is too detailed to wear, even simplified. Try one with fewer faces.");
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
