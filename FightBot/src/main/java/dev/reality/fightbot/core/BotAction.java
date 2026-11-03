package dev.reality.fightbot.core;

/**
 * Inputs for one tick, the same set a player has. forward and strafe are -1, 0 or 1,
 * with strafe 1 meaning left. hotbar is 0 to 8, or -1 to keep the current slot.
 */
public final class BotAction {
    public int forward;
    public int strafe;
    public boolean sprint;
    public boolean sneak;
    public boolean jump;
    /** Holds the jump key down, which is how a player swims up. A single jump does nothing off the ground. */
    public boolean holdJump;
    /** This tick fires a shot, so the aim goes in exactly as the policy set it. */
    public boolean exactAim;
    public boolean attack;
    /** Hold right click, for eating. */
    public boolean use;
    /** Hold right click with the shield in the offhand. */
    public boolean block;
    /** Item to move from the inventory into the hotbar, or null. */
    public String restock;
    public int hotbar = -1;
    /** Right click once with the selected item: a firework while gliding, or a wind charge. */
    public boolean useOnce;
    /** Swap the chest slot to "elytra" or to a chestplate ("chestplate"), or null. */
    public String equip;
    /** Open the elytra. Only works in the air while wearing one. */
    public boolean startGlide;
    /**
     * Swap hits: hotbar slots to switch to and hit with, in order, all inside this tick. One slot is an
     * attribute swap, like holding a charged sword and hitting with the mace. Two slots is a stun slam:
     * the axe disables the shield and the mace hits right after. Null for none.
     */
    public int[] swapAttack;
    /** Hotbar slot of a Lunge spear to jab with this tick, switching back to swapBack right after. -1 for none. */
    public int jabSlot = -1;
    /** Slot to end the tick on after swap hits or a jab. -1 stays on the last one used. */
    public int swapBack = -1;
    /** Put the totem in the selected hotbar slot into the offhand, like pressing the swap hands key. */
    public boolean swapHands;
    /**
     * Put a totem from anywhere in the inventory into the empty offhand, like hovering it in the inventory
     * and pressing the swap hands key. It does not use up the tick, so it goes along with any other input.
     */
    public boolean offhandTotem;
    /** A right click on a block face: placing obsidian, a crystal, or an anchor, or charging or setting off an anchor. */
    public dev.reality.fightbot.core.place.PlaceRequest place;
    /** Entity id of an end crystal to hit, or -1. */
    public int attackEntity = -1;
    /** Start a shulker box restock: place a box, take what is missing, and pick the box back up. */
    public boolean shulkerRestock;
    /** Item to right click into the air this tick, the way a player throws a pearl or empties a bucket, aimed with yaw and pitch. */
    public String useItem;
    /** Block to keep mining this tick, x, y, z, with the tool in mineSlot. */
    public int[] mine;
    public int mineSlot = -1;
    public float yaw, pitch;
    /** Which strategic state and technique chose this, for logs. */
    public String state = "";
    public String technique = "";

    public static BotAction idle(float yaw, float pitch) {
        BotAction a = new BotAction();
        a.yaw = yaw;
        a.pitch = pitch;
        a.state = "IDLE";
        return a;
    }

    public String moveName() {
        if (forward == 0 && strafe == 0) return "still";
        if (Math.abs(forward) >= Math.abs(strafe)) return forward > 0 ? "approach" : "retreat";
        return strafe > 0 ? "strafe_left" : "strafe_right";
    }
}
