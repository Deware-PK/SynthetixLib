package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixexample.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.core.pdc.PdcKey;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-tests for {@code core.pdc}. */
final class CorePdcSelfTests {

    private CorePdcSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        return List.of(
                SelfTest.sync("core.pdc.item.string", () ->
                        roundTrip(lib.keys().string("t_string"), "hello", "other", Object::equals)),
                SelfTest.sync("core.pdc.item.integer", () ->
                        roundTrip(lib.keys().integer("t_integer"), 42, 7, Object::equals)),
                SelfTest.sync("core.pdc.item.long", () ->
                        roundTrip(lib.keys().longKey("t_long"), 1L << 40, 3L, Object::equals)),
                SelfTest.sync("core.pdc.item.double", () ->
                        roundTrip(lib.keys().doubleKey("t_double"), 3.25D, 1.5D, Object::equals)),
                SelfTest.sync("core.pdc.item.bool", () ->
                        roundTrip(lib.keys().bool("t_bool"), true, false, Object::equals)),
                SelfTest.sync("core.pdc.item.uuid", () ->
                        roundTrip(lib.keys().uuid("t_uuid"), UUID.randomUUID(), UUID.randomUUID(), Object::equals)),
                SelfTest.sync("core.pdc.item.bytes", () ->
                        roundTrip(lib.keys().bytes("t_bytes"), new byte[] {1, 2, 3}, new byte[] {9},
                                Arrays::equals)),
                SelfTest.sync("core.pdc.item.of", () ->
                        roundTrip(lib.keys().of("t_of", PersistentDataType.LONG_ARRAY), new long[] {5L, 6L},
                                new long[] {1L}, Arrays::equals)),
                SelfTest.sync("core.pdc.item.empty", () -> {
                    final PdcKey<String> key = lib.keys().string("t_empty");
                    final ItemStack air = new ItemStack(Material.AIR);
                    require(key.get(air).isEmpty() && !key.has(air), "empty stack: get empty, has false");
                    for (final Runnable op : List.<Runnable>of(() -> key.set(air, "x"), () -> key.remove(air))) {
                        boolean rejected = false;
                        try {
                            op.run();
                        } catch (final IllegalArgumentException expected) {
                            rejected = "empty item".equals(expected.getMessage());
                        }
                        require(rejected, "set/remove on empty stack must throw IAE(\"empty item\")");
                    }
                }),
                SelfTest.sync("core.pdc.key-namespace", () -> {
                    final PdcKey<String> key = lib.keys().string("ns_check");
                    require(key.key().getNamespace().equals(plugin.getName().toLowerCase()),
                            "namespace must be the consumer plugin, got " + key.key().getNamespace());
                    boolean rejected = false;
                    try {
                        lib.keys().string("Bad Name");
                    } catch (final IllegalArgumentException expected) {
                        rejected = expected.getMessage().contains("Bad Name");
                    }
                    require(rejected, "invalid name must throw IAE mentioning the name");
                }),

                SelfTest.async("core.pdc.entity", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    final Location location = plugin.getServer().getWorlds().get(0).getSpawnLocation();
                    final PdcKey<String> text = lib.keys().string("e_string");
                    final PdcKey<UUID> uuid = lib.keys().uuid("e_uuid");
                    lib.scheduler().region(location).run(() -> {
                        ArmorStand stand = null;
                        try {
                            stand = location.getWorld().spawn(location, ArmorStand.class, s -> {
                                s.setMarker(true);
                                s.setInvisible(true);
                                s.setPersistent(false);
                            });
                            final UUID id = UUID.randomUUID();
                            require(!text.has(stand), "fresh entity must not have the key");
                            text.set(stand, "entity");
                            uuid.set(stand, id);
                            require(text.get(stand).filter("entity"::equals).isPresent(), "entity string mismatch");
                            require(uuid.get(stand).filter(id::equals).isPresent(), "entity uuid mismatch");
                            text.remove(stand);
                            require(text.get(stand).isEmpty(), "remove must clear the value");
                            require(uuid.has(stand), "removing one key must keep the other");
                            done.complete(null);
                        } catch (final Throwable t) {
                            done.completeExceptionally(t);
                        } finally {
                            if (stand != null) {
                                stand.remove();
                            }
                        }
                    });
                    return done;
                })
        );
    }

    private static <T> void roundTrip(final PdcKey<T> key, final T value, final T other,
                                      final BiPredicate<T, T> equal) {
        final ItemStack item = new ItemStack(Material.STONE);
        require(key.get(item).isEmpty() && !key.has(item), "fresh item must not have the key");
        key.set(item, value);
        require(key.has(item), "has() must be true after set");
        require(key.get(item).filter(v -> equal.test(v, value)).isPresent(), "get() must return the stored value");
        key.set(item, other);
        require(key.get(item).filter(v -> equal.test(v, other)).isPresent(), "set() must overwrite");
        // The stored value must survive a clone (it lives in the stack's components, not in a side table).
        require(key.get(item.clone()).filter(v -> equal.test(v, other)).isPresent(), "value lost on clone");
        key.remove(item);
        require(key.get(item).isEmpty() && !key.has(item), "remove() must clear the value");
    }
}
