package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.gui.file.ClickBindings;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Codecs and registrations for the tests; the Bukkit codecs need a server, so a plain Material codec stands in. */
final class GuiFixtures {

    /** A host for tests that never open a menu. */
    static final ViewHost NO_HOST = new ViewHost() {
        @Override
        public void open(final MenuType<?> type, final Player player, final Object context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Component previewTitle(final MenuType<?> type, final String playerName, final Object context,
                                      final int page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Inventory preview(final MenuType<?> type, final String playerName, final Object context,
                                 final int page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Inventory attach(final MenuType<?> type, final Player player, final Object context) {
            throw new UnsupportedOperationException();
        }
    };

    private GuiFixtures() {
    }

    static Codecs codecs() {
        final Codecs codecs = Codecs.create();
        codecs.register(Material.class, Codec.string(text -> {
            final Material material = Material.matchMaterial(text);
            if (material == null) {
                throw new IllegalArgumentException("unknown material '" + text + "'");
            }
            return material;
        }, material -> material.name().toLowerCase()));
        codecs.register(ClickBindings.class, new ClickBindingsCodec());
        return codecs;
    }

    /** Registrations with the given names; the handlers do nothing. */
    static Registrations<Void> registered(final List<String> placeholders, final List<String> lists,
                                          final List<String> states, final List<String> actions) {
        final Function<MenuView<Void>, Component> value = view -> Component.empty();
        final Consumer<MenuClick<Void>> handler = click -> { };
        return new Registrations<>(
                placeholders.stream().collect(java.util.stream.Collectors.toMap(n -> n, n -> value)),
                lists.stream().collect(java.util.stream.Collectors.toMap(n -> n,
                        n -> new Registrations.ListBinding<Void>(view -> List.of(), (entry, view) -> null, null))),
                states.stream().collect(java.util.stream.Collectors.toMap(n -> n, n -> (Function<MenuView<Void>, String>) view -> "")),
                actions.stream().collect(java.util.stream.Collectors.toMap(n -> n, n -> handler)));
    }

    static Registrations<Void> nothing() {
        return registered(List.of(), List.of(), List.of(), List.of());
    }

    static <K, V> Map<K, V> map(final K key, final V value) {
        return Map.of(key, value);
    }
}
