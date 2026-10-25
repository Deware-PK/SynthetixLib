package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.dialog.file.ClickActions;
import org.bukkit.Material;

/** Codecs for the tests; the Bukkit codecs need a server, so a plain Material codec stands in. */
final class DialogFixtures {

    /** A host for tests that never show a dialog. */
    static final ViewHost NO_HOST = new ViewHost() {
        @Override
        public <C> void open(final DialogType<C> type, final org.bukkit.entity.Player player, final C context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <C> io.papermc.paper.dialog.Dialog preview(final DialogType<C> type, final String playerName,
                                                          final C context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <C> DialogViewImpl<C> attach(final DialogType<C> type, final org.bukkit.entity.Player player,
                                            final C context) {
            throw new UnsupportedOperationException();
        }
    };

    private DialogFixtures() {
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
        codecs.register(ClickActions.class, new ClickActionsCodec());
        codecs.register(java.math.BigDecimal.class, new BigDecimalCodec());
        return codecs;
    }
}
