/**
 * The parsed model of a dialog file ({@code dialogs/<id>.yml}): immutable records that the config codecs fill in.
 * Public only so the config module can map them; read by the rest of the dialog module and by tools, not meant to be
 * constructed by plugins.
 */
@org.jetbrains.annotations.ApiStatus.Internal
package com.github.dewarepk.synthetixlib.dialog.file;
