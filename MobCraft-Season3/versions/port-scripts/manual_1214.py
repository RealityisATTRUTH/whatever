"""1.21.4 hand edits on the 1.21.5 copy (after files_1214 is copied in).
1.21.5 renamed mob effects and movement methods, moved NBT getters to Optionals,
made BakedQuad a record and brought in render pipelines; 1.21.4 has the older forms."""
import sys, re, pathlib
root = pathlib.Path(sys.argv[1]) / 'com/reality/mobcraft'
res = root.parents[3] / 'resources'

def edit(rel, old, new, count=1):
    p = root / rel; s = p.read_text()
    assert old in s, (rel, old[:70])
    p.write_text(s.replace(old, new, count if count else -1))

def sub_all(pattern, repl, flags=0):
    for p in root.rglob('*.java'):
        s = p.read_text(); t = re.sub(pattern, repl, s, flags=flags)
        if t != s: p.write_text(t)

# Mob effects: the older names
for new, old in [('SPEED', 'MOVEMENT_SPEED'), ('SLOWNESS', 'MOVEMENT_SLOWDOWN'), ('HASTE', 'DIG_SPEED'),
                 ('MINING_FATIGUE', 'DIG_SLOWDOWN'), ('STRENGTH', 'DAMAGE_BOOST'), ('INSTANT_HEALTH', 'HEAL'),
                 ('INSTANT_DAMAGE', 'HARM'), ('JUMP_BOOST', 'JUMP'), ('NAUSEA', 'CONFUSION'), ('RESISTANCE', 'DAMAGE_RESISTANCE')]:
    sub_all(r'\bMobEffects\.' + new + r'\b', 'MobEffects.' + old)
# Moving entities
sub_all(r'\.snapTo\(', '.moveTo(')
sub_all(r'\.absSnapTo\(', '.absMoveTo(')
sub_all(r'\.isDarkOutside\(\)', '.isNight()')
sub_all(r'\.getInventory\(\)\.getSelectedSlot\(\)', '.getInventory().selected')
sub_all(r'SoundEvents\.(ITEM_BREAK|SHIELD_BREAK)\.value\(\)', r'SoundEvents.\1')
sub_all(r'new ClickEvent\.RunCommand\(', 'new ClickEvent(ClickEvent.Action.RUN_COMMAND, ')
sub_all(r'net\.minecraft\.core\.component\.DataComponents\.BLOCKS_ATTACKS\)', 'net.minecraft.core.component.DataComponents.EQUIPPABLE) && vp.getItemInHand(h).getItem() instanceof net.minecraft.world.item.ShieldItem')
edit('item/GhastWisp.java', 'import net.minecraft.util.random.WeightedList;\n', '')
edit('item/ModItems.java', '''        Item.Properties properties = new Item.Properties().setId(key)
                .humanoidArmor(SkullVisor.MATERIAL, net.minecraft.world.item.equipment.ArmorType.HELMET)''',
     '''        Item.Properties properties = SkullVisor.MATERIAL.humanoidProperties(new Item.Properties().setId(key),
                        net.minecraft.world.item.equipment.ArmorType.HELMET)''')
edit('mixin/client/UpdateLogScrollMixin.java', 'double x = mouse.getScaledXPos(client.getWindow());',
     'double x = mouse.xpos() * client.getWindow().getGuiScaledWidth() / client.getWindow().getScreenWidth();')
edit('mixin/client/UpdateLogScrollMixin.java', 'double y = mouse.getScaledYPos(client.getWindow());',
     'double y = mouse.ypos() * client.getWindow().getGuiScaledHeight() / client.getWindow().getScreenHeight();')
print('1.21.4 edits applied')

def drop_lines(rel, *lines):
    for l in lines: edit(rel, l, '')

sub_all(r'(\w+)\.getStringOr\(([^,()]+), ""\)', r'\1.getString(\2)')
for f in ['client/BlindOverlay.java', 'client/ArmorIconOverlay.java', 'client/SenseMarkers.java']:
    drop_lines(f, 'import net.minecraft.client.renderer.RenderPipelines;\n')

# The charged glow: vanilla's energy swirl. Here the glow is drawn right after the item, so its depth writes are fine.
p = root / 'client/ExplosiveItemGlow.java'; s = p.read_text()
a = s.index('    private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline;')
b = s.index('    /** Set while a menu draws')
s = s[:a] + '''    private static net.minecraft.client.renderer.RenderType swirl(float u, float v) {
        return net.minecraft.client.renderer.RenderType.energySwirl(TEXTURE, u, v);
    }

''' + s[b:]
s = s.replace('''    /**
     * Vanilla's energy swirl, but it doesn't write depth. 26.1 can draw the glow before the item it wraps, and the
     * vanilla swirl then hides the item behind it. Without depth writes, the order doesn't matter.
     */
''', '    /** Vanilla energy swirl, drawn just after the item it wraps. */\n')
s = s.replace('quad.direction()', 'quad.getDirection()').replace('quad.sprite()', 'quad.getSprite()').replace('quad.vertices()', 'quad.getVertices()')
p.write_text(s)
(root / 'mixin/client/RenderPipelinesAccessor.java').unlink()
cj = res / 'mobcraft.client.mixins.json'; t = cj.read_text()
assert '    "RenderPipelinesAccessor",\n' in t
cj.write_text(t.replace('    "RenderPipelinesAccessor",\n', ''))

# Item layers hold a baked model here, not a list of faces
edit('mixin/client/ItemLayerGlowMixin.java', '@Shadow @org.spongepowered.asm.mixin.Final private List<BakedQuad> quads;',
     '@Shadow net.minecraft.client.resources.model.BakedModel model;')
edit('mixin/client/ItemLayerGlowMixin.java', 'ExplosiveItemGlow.submit(poseStack, new SubmitNodeCollector(buffers), quads);',
     'ExplosiveItemGlow.submit(poseStack, new SubmitNodeCollector(buffers), mobcraft$quads());')
edit('mixin/client/ItemLayerGlowMixin.java', '    @Override\n    public void mobcraft$markExplosive() {', '''    /** Every face of the layer's model, as the game draws them. */
    @Unique
    private List<BakedQuad> mobcraft$quads() {
        if (model == null) return List.of();
        List<BakedQuad> out = new java.util.ArrayList<>();
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            random.setSeed(42L);
            out.addAll(model.getQuads(null, d, random));
        }
        random.setSeed(42L);
        out.addAll(model.getQuads(null, null, random));
        return out;
    }

    @Override
    public void mobcraft$markExplosive() {''')
edit('compat/SubmitNodeCollector.java', '        ItemRenderer.renderItem(context, pose, buffers, light, overlay, tints, quads, type, foil);',
     '''        com.mojang.blaze3d.vertex.VertexConsumer buffer = ItemRenderer.getFoilBuffer(buffers, type, true, foil != ItemStackRenderState.FoilType.NONE);
        for (BakedQuad quad : quads) buffer.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, light, overlay);''')

# Items tick in inventories with a slot number and a selected flag
edit('item/StrongholdTome.java', '''    public void inventoryTick(ItemStack stack, ServerLevel level, net.minecraft.world.entity.Entity owner,
                              net.minecraft.world.entity.EquipmentSlot slot) {''', '''    public void inventoryTick(ItemStack stack, Level anyLevel, net.minecraft.world.entity.Entity owner, int slotIndex, boolean selected) {
        if (!(anyLevel instanceof ServerLevel level)) return;
        net.minecraft.world.entity.EquipmentSlot slot = selected ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                : owner instanceof Player p && p.getOffhandItem() == stack ? net.minecraft.world.entity.EquipmentSlot.OFFHAND : null;''')
print('1.21.4 render edits applied')

# Sounds: the player who hears no echo is a Player here
s = (root / 'mixin/ServerLevelSoundMixin.java').read_text()
s = s.replace('playSeededSound(Lnet/minecraft/world/entity/Entity;', 'playSeededSound(Lnet/minecraft/world/entity/player/Player;')
s = s.replace('(Entity except,', '(net.minecraft.world.entity.player.Player except,')
(root / 'mixin/ServerLevelSoundMixin.java').write_text(s)
# First-person items: renderStatic also takes which hand
p = root / 'mixin/client/BackwardsFirstPersonMixin.java'; s = p.read_text()
s = s.replace('Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;', 'Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;')
s = s.replace('ItemStack stack, net.minecraft.world.item.ItemDisplayContext context, PoseStack pose,',
              'ItemStack stack, net.minecraft.world.item.ItemDisplayContext context, boolean leftHand, PoseStack pose,')
s = s.replace('original.call(renderer, entity, stack, context, pose,', 'original.call(renderer, entity, stack, context, leftHand, pose,')
p.write_text(s)
print('1.21.4 mixin edits applied')

# Anvil: "creative" is the abilities flag here
edit('mixin/AnvilKeepLevelsMixin.java', '''    @WrapOperation(method = "createResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;hasInfiniteMaterials()Z"))
    private boolean mobcraft$neverTooExpensive(Player player, Operation<Boolean> original) {
        return original.call(player) || mobcraft$strong();
    }''', '''    @WrapOperation(method = "createResult", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Abilities;instabuild:Z"))
    private boolean mobcraft$neverTooExpensive(net.minecraft.world.entity.player.Abilities abilities, Operation<Boolean> original) {
        return original.call(abilities) || mobcraft$strong();
    }''')
print('1.21.4 anvil edit applied')

# The mace's hit reports whether it hit
edit('mixin/MaceSmashMixin.java', 'LivingEntity attacker, CallbackInfo ci)',
     'LivingEntity attacker, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci)')
print('1.21.4 mace edit applied')

# Data: text inside item components is written as a JSON string here
import json
TEXT_KEYS = ('minecraft:item_name', 'minecraft:custom_name')
def fix_components(n):
    if isinstance(n, dict):
        for k, v in list(n.items()):
            if k == 'components' and isinstance(v, dict):
                for tk in TEXT_KEYS:
                    if isinstance(v.get(tk), (dict, list)): v[tk] = json.dumps(v[tk], separators=(',', ':'))
                if isinstance(v.get('minecraft:lore'), list):
                    v['minecraft:lore'] = [json.dumps(x, separators=(',', ':')) if isinstance(x, (dict, list)) else x for x in v['minecraft:lore']]
            fix_components(v)
    elif isinstance(n, list):
        for v in n: fix_components(v)
for f in (res / 'data').rglob('*.json'):
    d = json.loads(f.read_text()); before = json.dumps(d)
    fix_components(d)
    if json.dumps(d) != before: f.write_text(json.dumps(d, indent=2))
print('1.21.4 data edits applied')

# Update log
log = res / 'assets/mobcraft/update_log.txt'; s = log.read_text()
s = s.replace(' for Minecraft 1.21.5\n', ' for Minecraft 1.21.4\n')
s = s.replace('- Now made to run on Minecraft 1.21.5 with Fabric API for 1.21.5\n', '- Now made to run on Minecraft 1.21.4 with Fabric API for 1.21.4\n', 1)
s = s.replace('so on 1.21.5 the Ghast', 'so on 1.21.4 the Ghast', 1)
log.write_text(s)
print('1.21.4 update log')
