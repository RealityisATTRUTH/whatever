package com.reality.mobcraft.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 26.1 keeps team colors as ChatFormatting. This stands in for 26.2's TeamColor so the team code reads the same.
 * Each color has one instance, so == works.
 */
public final class TeamColor {
    public static final List<TeamColor> VALUES;
    static {
        List<TeamColor> all = new ArrayList<>();
        for (ChatFormatting f : ChatFormatting.values()) if (f.isColor()) all.add(new TeamColor(f));
        VALUES = List.copyOf(all);
    }

    private final ChatFormatting format;

    private TeamColor(ChatFormatting format) { this.format = format; }

    public ChatFormatting format() { return format; }
    public String getSerializedName() { return format.getSerializedName(); }
    public int rgb() { return format.getColor() == null ? 0xFFFFFF : format.getColor(); }

    /** The color for a formatting code. Anything that isn't a color (like reset) gives white. */
    public static TeamColor from(ChatFormatting f) {
        for (TeamColor c : VALUES) if (c.format == f) return c;
        return from(ChatFormatting.WHITE);
    }

    public static Optional<TeamColor> of(PlayerTeam team) {
        ChatFormatting f = team.getColor();
        return f == null || !f.isColor() ? Optional.empty() : Optional.of(from(f));
    }

    public static void set(PlayerTeam team, Optional<TeamColor> color) {
        team.setColor(color.map(TeamColor::format).orElse(ChatFormatting.RESET));
    }
}
