package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import com.github.dewarepk.synthetixlib.dialog.file.DialogFile;
import com.github.dewarepk.synthetixlib.dialog.file.DialogKind;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What a registry (bootstrap) dialog may use, and the settings that go with the registry work. */
class RegistryDialogsTest {

    private DialogFile file(final String yaml) {
        return new DialogParser(DialogFixtures.codecs()).parse(yaml).file();
    }

    private static List<String> paths(final List<Problem> problems) {
        return problems.stream().map(Problem::path).toList();
    }

    @Test
    void aStaticDialogWithOnlyStandardTagsHasNoProblems() {
        final DialogFile file = file("""
                title: '<gold>Help <bold>now</bold> <lang:gui.yes>'
                type: notice
                body:
                  - message: '<gray>Hello<newline><red>world'
                  - item: { material: diamond, name: '<aqua>Prize' }
                buttons:
                  ok: { label: '<green>Fine', click: [close] }
                """);

        assertEquals(List.of(), SynthetixDialogs.staticProblems(file));
    }

    @Test
    void placeholdersAndPlayerItemsAreReportedWithTheirPath() {
        final DialogFile file = file("""
                title: 'Hello <player>'
                type: notice
                body:
                  - message: '<gray>Fee: <fee> <fee>'
                  - item: { slot: hand }
                  - item: { placeholder: lot-item }
                buttons:
                  ok: { label: 'Ok', click: [close] }
                """);

        final List<Problem> problems = SynthetixDialogs.staticProblems(file);

        assertEquals(List.of("title", "body[0].message", "body[1].item", "body[2].item"), paths(problems));
    }

    @Test
    void anExitButtonOrAConfirmationNoThatOnlyClosesEndsWithoutAnAnswer() {
        final List<ClickAction> close = List.of(new ClickAction.Close());
        final List<ClickAction> run = List.of(new ClickAction.Run("sell"));

        assertTrue(DialogRenderer.isExit(DialogKind.MULTI_ACTION, "buttons.exit", run));
        assertTrue(DialogRenderer.isExit(DialogKind.CONFIRMATION, "buttons.no", close));
        assertFalse(DialogRenderer.isExit(DialogKind.CONFIRMATION, "buttons.no", run));
        assertFalse(DialogRenderer.isExit(DialogKind.CONFIRMATION, "buttons.yes", close));
        assertFalse(DialogRenderer.isExit(DialogKind.MULTI_ACTION, "buttons.close", close));
    }

    @Test
    void settingsHaveADebugFlagThatIsOffByDefault() {
        assertFalse(DialogSettings.defaults().debug());
        assertFalse(new DialogSettings(Duration.ofMinutes(1)).debug());
        assertTrue(new DialogSettings(Duration.ofMinutes(1), true).debug());
        assertEquals(Duration.ofMinutes(5), DialogSettings.defaults().callbackLifetime());
    }
}
