"""Research-only: after the client starts, force every mixin target class to load so any hook that can't apply
shows up now, not mid-game. Not part of the port."""
import sys, pathlib
f = pathlib.Path(sys.argv[1]) / 'com/reality/mobcraft/client/MobCraftClient.java'; s = f.read_text()
hook = '''        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(c -> {
            org.slf4j.LoggerFactory.getLogger("mixinaudit").info("audit start");
            org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
            org.slf4j.LoggerFactory.getLogger("mixinaudit").info("audit done");
        });
'''
s = s.replace('    public void onInitializeClient() {\n', '    public void onInitializeClient() {\n' + hook, 1)
f.write_text(s)
