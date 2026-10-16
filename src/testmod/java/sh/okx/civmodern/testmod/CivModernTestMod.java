package sh.okx.civmodern.testmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Entrypoint of the dev-only testmod, loaded by {@code ./gradlew runClient} beside CivModern and
 * never packaged into the release jar. It runs for the integrated server too, which is where
 * the fake {@code /jalist} is registered.
 */
public class CivModernTestMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FakeJalist.register(dispatcher));
    }
}
