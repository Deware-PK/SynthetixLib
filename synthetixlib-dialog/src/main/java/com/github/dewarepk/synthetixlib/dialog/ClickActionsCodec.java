package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import com.github.dewarepk.synthetixlib.dialog.file.ClickActions;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code click:} of a button: one text or a list of texts. A text that is not an action does not fail the button: it
 * is reported as a warning at its path and the button gets no click behaviour at all ("inert"), so a half-valid list
 * never runs part of its steps.
 */
final class ClickActionsCodec implements Codec<ClickActions> {

    @Override
    public ClickActions decode(final Object raw, final DecodeContext ctx) {
        final List<ClickAction> out = new ArrayList<>();
        boolean bad = false;
        if (raw instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                bad |= !action(list.get(i), ctx.at(i), out);
            }
        } else {
            bad = !action(raw, ctx, out);
        }
        return bad ? ClickActions.none() : new ClickActions(out);
    }

    private static boolean action(final Object raw, final DecodeContext ctx, final List<ClickAction> out) {
        if (!(raw instanceof CharSequence text)) {
            ctx.warn("expected an action as text, got " + (raw == null ? "nothing" : raw.getClass().getSimpleName()));
            return false;
        }
        try {
            out.add(ClickAction.parse(text.toString()));
            return true;
        } catch (final IllegalArgumentException e) {
            ctx.warn(e.getMessage());
            return false;
        }
    }

    @Override
    public Object encode(final ClickActions value) {
        return value.actions().stream().map(ClickAction::format).toList();
    }
}
