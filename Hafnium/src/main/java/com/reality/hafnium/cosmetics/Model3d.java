// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A 3D cosmetic made in Blockbench, in the small form Hafnium sends and stores.
 *
 * The creator saves their work as a normal Blockbench project (.bbmodel).
 * Hafnium keeps only what's needed to draw it: each cube's corners, its
 * rotation and the part of the texture on each face, plus the texture itself.
 * Folders, names, animations and editor settings are left out. The result
 * is packed with gzip before uploading.
 *
 * Build the model around Blockbench's standard player, with the feet at
 * height 0, the neck at height 24 and the top of the head at 32, facing north.
 *
 * Models from Sketchfab or a .glb file are a mesh of triangles instead of
 * cubes (see GltfImport). They use the same space, and their corners are kept
 * to a sixteenth of a pixel.
 */
public final class Model3d {

    public static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    /** One cube. Face UVs are u1, v1, u2, v2 and a rotation of 0, 90, 180 or 270. */
    public record Cube(float[] from, float[] to, float[] origin, float[] rot, Map<String, float[]> faces) { }

    public final List<Cube> cubes;
    public final int texW;
    public final int texH;
    public final byte[] texture;
    /** A mesh's corners as x, y, z, its texture spots as u, v from 0 to 1, and its triangles as corner numbers. Null without a mesh. */
    public final float[] meshPos;
    public final float[] meshUv;
    public final int[] meshTris;

    private Model3d(List<Cube> cubes, int texW, int texH, byte[] texture) {
        this(cubes, texW, texH, texture, null, null, null);
    }

    Model3d(List<Cube> cubes, int texW, int texH, byte[] texture, float[] meshPos, float[] meshUv, int[] meshTris) {
        this.cubes = cubes;
        this.texW = texW;
        this.texH = texH;
        this.texture = texture;
        this.meshPos = meshPos;
        this.meshUv = meshUv;
        this.meshTris = meshTris;
    }

    public boolean hasMesh() {
        return meshTris != null && meshTris.length > 0;
    }

    public int triangles() {
        return hasMesh() ? meshTris.length / 3 : 0;
    }

    // --- From a Blockbench project ----------------------------------------------------------

    /** Reads a .bbmodel file. Throws with a plain-words message if it can't be used. */
    public static Model3d fromBlockbench(Path file) throws IOException {
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("That isn't a Blockbench project file.");
        }
        JsonArray textures = root.has("textures") ? root.getAsJsonArray("textures") : new JsonArray();
        if (textures.isEmpty()) throw new IOException("The model needs a texture.");
        if (textures.size() > 1) throw new IOException("Use one texture for the whole model.");
        String source = textures.get(0).getAsJsonObject().has("source") ? textures.get(0).getAsJsonObject().get("source").getAsString() : "";
        int comma = source.indexOf(',');
        if (!source.startsWith("data:image/png;base64,") || comma < 0) throw new IOException("Save the texture inside the project (it must be a PNG).");
        byte[] png = Base64.getDecoder().decode(source.substring(comma + 1));

        int tw = 16, th = 16;
        if (root.has("resolution")) {
            JsonObject r = root.getAsJsonObject("resolution");
            tw = r.get("width").getAsInt();
            th = r.get("height").getAsInt();
        }

        List<Cube> cubes = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            String type = el.has("type") ? el.get("type").getAsString() : "cube";
            if (!type.equals("cube")) throw new IOException("Only cubes are supported, not meshes.");
            if (el.has("visibility") && !el.get("visibility").getAsBoolean()) continue;
            float grow = el.has("inflate") ? el.get("inflate").getAsFloat() : 0;
            float[] from = vec(el.get("from")), to = vec(el.get("to"));
            for (int a = 0; a < 3; a++) {
                float lo = Math.min(from[a], to[a]) - grow, hi = Math.max(from[a], to[a]) + grow;
                from[a] = lo;
                to[a] = hi;
            }
            float[] origin = el.has("origin") ? vec(el.get("origin")) : new float[3];
            float[] rot = el.has("rotation") ? vec(el.get("rotation")) : new float[3];
            Map<String, float[]> faces = new LinkedHashMap<>();
            JsonObject fs = el.getAsJsonObject("faces");
            if (fs != null) {
                for (String f : FACES) {
                    if (!fs.has(f)) continue;
                    JsonObject face = fs.getAsJsonObject(f);
                    if (!face.has("uv") || (face.has("texture") && face.get("texture").isJsonNull())) continue;
                    JsonArray uv = face.getAsJsonArray("uv");
                    float r = face.has("rotation") ? face.get("rotation").getAsFloat() : 0;
                    faces.put(f, new float[]{uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat(), r});
                }
            }
            if (!faces.isEmpty()) cubes.add(new Cube(from, to, origin, rot, faces));
        }
        if (cubes.isEmpty()) throw new IOException("The model has no cubes with a texture on them.");
        if (cubes.size() > 256) throw new IOException("The model has " + cubes.size() + " cubes. The most is 256.");
        return new Model3d(cubes, tw, th, png);
    }

    private static float[] vec(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    // --- The small form -------------------------------------------------------------------------

    /** The model as gzip-packed JSON, ready to upload. */
    public byte[] pack() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("v", hasMesh() ? 2 : 1);
        root.addProperty("tw", texW);
        root.addProperty("th", texH);
        root.addProperty("texture", Base64.getEncoder().encodeToString(texture));
        JsonArray list = new JsonArray();
        for (Cube c : cubes) {
            JsonObject o = new JsonObject();
            o.add("from", arr(c.from()));
            o.add("to", arr(c.to()));
            if (c.rot()[0] != 0 || c.rot()[1] != 0 || c.rot()[2] != 0) {
                o.add("origin", arr(c.origin()));
                o.add("rot", arr(c.rot()));
            }
            JsonObject faces = new JsonObject();
            c.faces().forEach((k, uv) -> faces.add(k, arr(uv[4] == 0 ? new float[]{uv[0], uv[1], uv[2], uv[3]} : uv)));
            o.add("faces", faces);
            list.add(o);
        }
        root.add("cubes", list);
        if (hasMesh()) {
            // Each corner is 10 bytes: x, y, z in sixteenths of a pixel, then u and v from 0 to 65535.
            int n = meshPos.length / 3;
            java.nio.ByteBuffer verts = java.nio.ByteBuffer.allocate(n * 10).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < n; i++) {
                for (int a = 0; a < 3; a++) verts.putShort((short) Math.round(meshPos[i * 3 + a] * 16));
                for (int a = 0; a < 2; a++) verts.putShort((short) Math.round(Math.max(0, Math.min(1, meshUv[i * 2 + a])) * 65535));
            }
            java.nio.ByteBuffer tris = java.nio.ByteBuffer.allocate(meshTris.length * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int t : meshTris) tris.putShort((short) t);
            JsonObject mesh = new JsonObject();
            mesh.addProperty("verts", Base64.getEncoder().encodeToString(verts.array()));
            mesh.addProperty("tris", Base64.getEncoder().encodeToString(tris.array()));
            root.add("mesh", mesh);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    /** Reads the small form back, refusing anything that unpacks to more than 1 MB. */
    public static Model3d unpack(byte[] packed) throws IOException {
        byte[] raw;
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(packed))) {
            raw = in.readNBytes(1024 * 1024 + 1);
        }
        if (raw.length > 1024 * 1024) throw new IOException("The model unpacks too big.");
        JsonObject root = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Cube> cubes = new ArrayList<>();
        JsonArray cubeList = root.has("cubes") ? root.getAsJsonArray("cubes") : new JsonArray();
        for (JsonElement e : cubeList) {
            JsonObject o = e.getAsJsonObject();
            Map<String, float[]> faces = new LinkedHashMap<>();
            JsonObject fs = o.getAsJsonObject("faces");
            for (String f : FACES) {
                if (!fs.has(f)) continue;
                JsonArray uv = fs.getAsJsonArray(f);
                faces.put(f, new float[]{uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat(),
                        uv.size() > 4 ? uv.get(4).getAsFloat() : 0});
            }
            cubes.add(new Cube(vec(o.get("from")), vec(o.get("to")),
                    o.has("origin") ? vec(o.get("origin")) : new float[3], o.has("rot") ? vec(o.get("rot")) : new float[3], faces));
        }
        float[] pos = null, uv = null;
        int[] tris = null;
        if (root.has("mesh")) {
            JsonObject mesh = root.getAsJsonObject("mesh");
            java.nio.ByteBuffer v = java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(mesh.get("verts").getAsString())).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            java.nio.ByteBuffer t = java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(mesh.get("tris").getAsString())).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int n = v.remaining() / 10;
            pos = new float[n * 3];
            uv = new float[n * 2];
            for (int i = 0; i < n; i++) {
                for (int a = 0; a < 3; a++) pos[i * 3 + a] = v.getShort() / 16f;
                for (int a = 0; a < 2; a++) uv[i * 2 + a] = (v.getShort() & 0xFFFF) / 65535f;
            }
            tris = new int[t.remaining() / 2 / 3 * 3];
            for (int i = 0; i < tris.length; i++) {
                tris[i] = t.getShort() & 0xFFFF;
                if (tris[i] >= n) throw new IOException("The model's mesh is broken.");
            }
        }
        return new Model3d(cubes, root.get("tw").getAsInt(), root.get("th").getAsInt(),
                Base64.getDecoder().decode(root.get("texture").getAsString()), pos, uv, tris);
    }

    private static JsonArray arr(float[] v) {
        JsonArray a = new JsonArray();
        for (float f : v) a.add(f == (int) f ? (Number) (int) f : (Number) f);
        return a;
    }
}
