package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import com.github.dewarepk.synthetixlib.gui.file.ClickAction;
import com.github.dewarepk.synthetixlib.gui.file.ClickBindings;
import com.github.dewarepk.synthetixlib.gui.file.ClickKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code click:} of an item: a list (or one text) for any click, or a section with one list per click type.
 * A text that is not an action does not fail the item: it is reported as a warning at its path and the item gets no
 * click behaviour at all ("inert"), so a half-valid list never runs part of its steps.
 */
final class ClickBindingsCodec implements Codec<ClickBindings> {

    /** Collects whether anything was wrong while one value is decoded; the codec itself stays stateless. */
    private static final class Pass {
        private boolean bad;
    }

    @Override
    public ClickBindings decode(final Object raw, final DecodeContext ctx) {
        final Pass pass = new Pass();
        final ClickBindings bindings;
        if (raw instanceof Map<?, ?> map) {
            final Map<ClickKind, List<ClickAction>> byKind = new EnumMap<>(ClickKind.class);
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                final String key = String.valueOf(entry.getKey());
                final ClickKind kind = ClickKind.of(key);
                if (kind == null) {
                    ctx.at(key).warn("unknown click type, use left, right, shift-left, shift-right, middle or drop");
                    pass.bad = true;
                    continue;
                }
                byKind.put(kind, actions(entry.getValue(), ctx.at(key), pass));
            }
            bindings = new ClickBindings(List.of(), byKind);
        } else {
            bindings = ClickBindings.any(actions(raw, ctx, pass));
        }
        return pass.bad ? ClickBindings.none() : bindings;
    }

    private static List<ClickAction> actions(final Object raw, final DecodeContext ctx, final Pass pass) {
        final List<ClickAction> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                action(list.get(i), ctx.at(i), pass, out);
            }
        } else {
            action(raw, ctx, pass, out);
        }
        return out;
    }

    private static void action(final Object raw, final DecodeContext ctx, final Pass pass,
                               final List<ClickAction> out) {
        if (!(raw instanceof CharSequence text)) {
            ctx.warn("expected an action as text, got " + (raw == null ? "nothing" : raw.getClass().getSimpleName()));
            pass.bad = true;
            return;
        }
        try {
            out.add(ClickAction.parse(text.toString()));
        } catch (final IllegalArgumentException e) {
            ctx.warn(e.getMessage());
            pass.bad = true;
        }
    }

    @Override
    public Object encode(final ClickBindings value) {
        if (value.byKind().isEmpty()) {
            return format(value.any());
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        value.byKind().forEach((kind, actions) -> out.put(kind.key(), format(actions)));
        return out;
    }

    private static List<String> format(final List<ClickAction> actions) {
        return actions.stream().map(ClickAction::format).toList();
    }
}
