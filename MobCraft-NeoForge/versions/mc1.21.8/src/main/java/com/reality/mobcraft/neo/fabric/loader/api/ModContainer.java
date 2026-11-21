package com.reality.mobcraft.neo.fabric.loader.api;

public final class ModContainer {
    private final net.neoforged.fml.ModContainer neo;
    public ModContainer(net.neoforged.fml.ModContainer neo) { this.neo = neo; }
    public Metadata getMetadata() { return new Metadata(); }
    public final class Metadata {
        public String getId() { return neo.getModId(); }
        public String getName() { return neo.getModInfo().getDisplayName(); }
        public Version getVersion() { return new Version(); }
    }
    public final class Version {
        public String getFriendlyString() { return neo.getModInfo().getVersion().toString(); }
        @Override public String toString() { return getFriendlyString(); }
    }
}
