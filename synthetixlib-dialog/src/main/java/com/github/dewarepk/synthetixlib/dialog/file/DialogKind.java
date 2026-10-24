package com.github.dewarepk.synthetixlib.dialog.file;

/**
 * The kind of dialog, written as {@code type:} in the file. Decides which keys of {@code buttons:} are valid.
 *
 * <pre>{@code
 * type: confirmation       # buttons: yes + no
 * }</pre>
 */
public enum DialogKind {

    /** One {@code ok} button. */
    NOTICE,
    /** {@code yes} and {@code no}. */
    CONFIRMATION,
    /** Any number of named buttons, an optional {@code exit} button and {@code columns}. */
    MULTI_ACTION,
    /** Buttons that open other dialogs: {@code dialogs}, {@code exit}, {@code columns}, {@code button-width}. */
    LIST,
    /** The server links of the client: {@code exit}, {@code columns}, {@code button-width}. */
    LINKS
}
