package com.github.dewarepk.synthetixlib.dialog;

/**
 * The one-use rule of a view (design section 4). Every time the dialog is shown the view starts a new generation; the
 * callbacks of that showing carry it. The first callback that arrives consumes the whole view: the other buttons of the
 * same showing, a replayed packet, a callback of an earlier showing and anything after the view ended do nothing.
 * Shared by the nested dialogs of a list, so one click anywhere in it ends them all.
 */
final class ViewState {

    private int generation;
    private boolean consumed;
    private boolean ended;

    /** @return the generation of a new showing; the buttons of the previous one stop working */
    synchronized int begin() {
        generation++;
        consumed = false;
        return generation;
    }

    /** @return {@code true} for the first call per generation, if the view has not ended; else ignore the click */
    synchronized boolean tryConsume(final int fromGeneration) {
        if (ended || generation == 0 || fromGeneration != generation || consumed) {
            return false;
        }
        consumed = true;
        return true;
    }

    /** The view is over; nothing runs any more. */
    synchronized void end() {
        ended = true;
    }

    synchronized boolean isEnded() {
        return ended;
    }

    synchronized int generation() {
        return generation;
    }
}
