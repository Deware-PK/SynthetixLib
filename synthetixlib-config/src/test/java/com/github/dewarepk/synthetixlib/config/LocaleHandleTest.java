package com.github.dewarepk.synthetixlib.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocaleHandleTest {

    public record Messages(@Comment("Placeholders: <amount>") String bidPlaced,
                           String outbid,
                           List<String> help,
                           String disabled) {

        static Messages defaults() {
            return new Messages("<green>You bid <amount>", "<red>You were outbid!",
                    List.of("<gold>/auction bid", "<gold>/auction info"), "");
        }
    }

    /** A record with a map section, like the {@code dialog: {key: text}} of a plugin's messages. */
    public record Sectioned(String title, Map<String, String> dialog) {

        static Sectioned defaults() {
            final Map<String, String> dialog = new java.util.LinkedHashMap<>();
            dialog.put("sell-title", "Sell");
            dialog.put("sell-error-too-high", "Too high");
            dialog.put("sell-note", "Note");
            return new Sectioned("Auction", dialog);
        }
    }

    /** Resources "inside the jar" and a logger; nothing else is needed from the server. */
    private static final class Host implements ConfigHost {
        final Map<String, String> resources = new HashMap<>();
        boolean closed;

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public Logger logger() {
            return Logger.getAnonymousLogger();
        }

        @Override
        public void runAsync(final Runnable task) {
            task.run();
        }

        @Override
        public void runGlobal(final Runnable task) {
            task.run();
        }

        @Override
        public @Nullable InputStream resource(final String path) {
            final String content = resources.get(path);
            return content == null ? null : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Remembers what is sent to it; has a locale pointer if given one, like a Paper player. */
    private static final class Capture implements Audience {
        final List<String> received = new ArrayList<>();
        private final @Nullable Locale locale;

        Capture(final @Nullable Locale locale) {
            this.locale = locale;
        }

        @Override
        public void sendMessage(final Component message) {
            received.add(plain(message));
        }

        @Override
        public Pointers pointers() {
            return locale == null ? Pointers.empty() : Pointers.builder().withStatic(Identity.LOCALE, locale).build();
        }
    }

    @TempDir
    Path root;

    private final Host host = new Host();

    private static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private Path lang() {
        return root.resolve("lang");
    }

    private void write(final String tag, final String text) throws IOException {
        Files.createDirectories(lang());
        Files.writeString(lang().resolve(tag + ".yml"), text, StandardCharsets.UTF_8);
    }

    private String read(final String tag) throws IOException {
        return Files.readString(lang().resolve(tag + ".yml"), StandardCharsets.UTF_8);
    }

    private LocaleHandle<Messages> handle(final boolean perPlayer, final Locale defaultLocale, final String... bundled) {
        return LocaleHandle.open(new LocaleHandle<>(host, TextService.create(), lang(), "lang", Messages.class,
                Messages::defaults, Codecs.create(), 1, List.of(), defaultLocale, List.of(bundled), perPlayer));
    }

    private LocaleHandle<Messages> handle(final boolean perPlayer) {
        return handle(perPlayer, Locale.US);
    }

    // --- files ------------------------------------------------------------------------------------------------

    @Test
    void theDefaultLocaleFileIsCreatedFromTheDefaultsWithComments() throws IOException {
        final LocaleHandle<Messages> lang = handle(false);

        final String text = read("en_US");
        assertTrue(text.contains("# Placeholders: <amount>\nbid-placed: <green>You bid <amount>"), text);
        assertTrue(text.contains("help:\n- <gold>/auction bid\n- <gold>/auction info"), text);
        assertEquals("You bid 100", plain(lang.get((Locale) null, Messages::bidPlaced,
                Placeholder.unparsed("amount", "100"))));
    }

    @Test
    void theDefaultLocaleCanBeSetAndNamesItsFile() throws IOException {
        final LocaleHandle<Messages> lang = handle(false, Locale.GERMANY);

        assertEquals(Locale.GERMANY, lang.defaultLocale());
        assertTrue(Files.exists(lang().resolve("de_DE.yml")));
        assertFalse(Files.exists(lang().resolve("en_US.yml")));
    }

    @Test
    void aBundledFileIsCopiedFromTheJarThenMissingKeysAreFilled() throws IOException {
        host.resources.put("lang/th_TH.yml", "outbid: <red>คุณถูกประมูลแซง\n");

        final LocaleHandle<Messages> lang = handle(true, Locale.US, "th_TH");

        final String text = read("th_TH");
        assertTrue(text.contains("outbid: <red>คุณถูกประมูลแซง"), text);
        assertTrue(text.contains("bid-placed: <green>You bid <amount>"), "filled from the defaults: " + text);
        assertEquals("คุณถูกประมูลแซง", plain(lang.get(Locale.of("th", "TH"), Messages::outbid)));
        assertEquals("You were outbid!", plain(lang.get(Locale.US, Messages::outbid)));
    }

    @Test
    void aCopiedBundledFileGetsTheCommentsOfTheRecordButKeepsItsOwn() throws IOException {
        host.resources.put("lang/th_TH.yml", "bid-placed: <green>ประมูล <amount>\n"
                + "# own comment\noutbid: <red>คุณถูกประมูลแซง\n");

        handle(true, Locale.US, "th_TH");

        final String text = read("th_TH");
        assertTrue(text.contains("# Placeholders: <amount>\nbid-placed: <green>ประมูล <amount>"), text);
        assertTrue(text.contains("# own comment\noutbid: <red>คุณถูกประมูลแซง"), text);
    }

    @Test
    void anExistingBundledFileKeepsItsBytesOnReload() throws IOException {
        host.resources.put("lang/th_TH.yml", "outbid: from the jar\n");
        write("th_TH", "outbid: edited\nbid-placed: a\nhelp: [x]\ndisabled: ''\n");
        final LocaleHandle<Messages> lang = handle(true, Locale.US, "th_TH");
        final Path file = lang().resolve("th_TH.yml");
        final String before = read("th_TH");
        final var modified = Files.getLastModifiedTime(file);

        lang.reload();

        assertEquals(before, read("th_TH"));
        assertEquals(modified, Files.getLastModifiedTime(file));
    }

    @Test
    void anExistingBundledFileIsNeverOverwritten() throws IOException {
        host.resources.put("lang/th_TH.yml", "outbid: from the jar\n");
        write("th_TH", "outbid: edited by the owner\n");

        final LocaleHandle<Messages> lang = handle(true, Locale.US, "th_TH");

        assertEquals("edited by the owner", plain(lang.get(Locale.of("th", "TH"), Messages::outbid)));
    }

    @Test
    void aBundledFileThatIsNotInTheJarIsReportedAndSkipped() {
        final LocaleHandle<Messages> lang = handle(true, Locale.US, "th_TH");

        final FilesReloadResult result = lang.reload();

        assertFalse(result.ok());
        assertTrue(result.files().get("th_TH").errors().get(0).contains("lang/th_TH.yml is not in the plugin jar"));
        assertTrue(result.files().get("en_US").ok());
        assertFalse(Files.exists(lang().resolve("th_TH.yml")));
    }

    @Test
    void invalidTagsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> handle(true, Locale.US, "TH"));
        assertThrows(IllegalArgumentException.class, () -> handle(true, Locale.US, "../x"));
        assertThrows(IllegalArgumentException.class, () -> handle(true, Locale.ROOT));
    }

    // --- choosing the language ----------------------------------------------------------------------------------

    private LocaleHandle<Messages> withLanguages() throws IOException {
        write("th_TH", "outbid: th_TH\n");
        write("th", "outbid: th\n");
        write("pt_PT", "outbid: pt_PT\n");
        write("en_GB", "outbid: en_GB\n");
        return handle(true);
    }

    private static String outbid(final LocaleHandle<Messages> lang, final Locale locale) {
        return plain(lang.get(locale, Messages::outbid));
    }

    @Test
    void theLanguageIsFoundExactThenByLanguageFileThenAnyOfTheSameLanguageThenDefault() throws IOException {
        final LocaleHandle<Messages> lang = withLanguages();

        assertEquals("th_TH", outbid(lang, Locale.of("th", "TH")), "exact");
        assertEquals("th", outbid(lang, Locale.of("th", "XX")), "the file of the language");
        assertEquals("th", outbid(lang, Locale.of("th")), "language only");
        assertEquals("pt_PT", outbid(lang, Locale.of("pt", "BR")), "another file of the same language");
        assertEquals("You were outbid!", outbid(lang, Locale.of("fr", "FR")), "default");
        assertEquals("en_GB", outbid(lang, Locale.of("en", "GB")), "exact beats the default's language");
        assertEquals("You were outbid!", outbid(lang, Locale.of("en", "AU")),
                "the default file wins among files of its own language");
    }

    @Test
    void tagsAreComparedIgnoringCase() throws IOException {
        final LocaleHandle<Messages> lang = withLanguages();

        assertEquals("th_TH", outbid(lang, Locale.of("th", "th")));
        assertEquals("th_TH", outbid(lang, Locale.forLanguageTag("TH-th")));
    }

    @Test
    void withoutPerPlayerEveryoneGetsTheDefault() throws IOException {
        write("th_TH", "outbid: th_TH\n");
        final LocaleHandle<Messages> lang = handle(false);

        final Capture thai = new Capture(Locale.of("th", "TH"));
        lang.send(thai, Messages::outbid);
        assertEquals(List.of("You were outbid!"), thai.received);
        assertEquals("You were outbid!", plain(lang.get(thai, Messages::outbid)));
    }

    @Test
    void anExplicitLocaleIsHonouredEvenWithoutPerPlayer() throws IOException {
        write("th_TH", "outbid: th_TH\nhelp: [thai help]\n");
        final LocaleHandle<Messages> lang = handle(false);

        assertEquals("th_TH", outbid(lang, Locale.of("th", "TH")));
        assertEquals("thai help", plain(lang.getLines(Locale.of("th", "TH"), Messages::help)));
        assertEquals("You were outbid!", outbid(lang, Locale.FRANCE), "unknown language: default");
    }

    @Test
    void anAudienceIsServedInItsOwnLocaleAndOneWithoutLocaleGetsTheDefault() throws IOException {
        final LocaleHandle<Messages> lang = withLanguages();
        final Capture thai = new Capture(Locale.of("th", "TH"));
        final Capture console = new Capture(null);

        lang.send(thai, Messages::outbid);
        lang.send(console, Messages::outbid);

        assertEquals(List.of("th_TH"), thai.received);
        assertEquals(List.of("You were outbid!"), console.received);
        assertEquals("th_TH", plain(lang.get(thai, Messages::outbid)));
        assertEquals("You were outbid!", plain(lang.get(console, Messages::outbid)));
    }

    // --- messages ------------------------------------------------------------------------------------------------

    @Test
    void placeholdersAreFilledAndPlayerTextIsNeverParsed() {
        final LocaleHandle<Messages> lang = handle(true);
        final Capture audience = new Capture(null);

        lang.send(audience, Messages::bidPlaced, Placeholder.unparsed("amount", "<red>100"));

        assertEquals(List.of("You bid <red>100"), audience.received);
    }

    @Test
    void anEmptyMessageIsDisabled() {
        final LocaleHandle<Messages> lang = handle(true);
        final Capture audience = new Capture(null);

        lang.send(audience, Messages::disabled);

        assertEquals(List.of(), audience.received);
        assertEquals(Component.empty(), lang.get(audience, Messages::disabled));
    }

    @Test
    void listMessagesAreOneLinePerEntry() {
        final LocaleHandle<Messages> lang = handle(true);
        final Capture audience = new Capture(null);

        lang.sendLines(audience, Messages::help);

        assertEquals(List.of("/auction bid", "/auction info"), audience.received);
        assertEquals("/auction bid\n/auction info", plain(lang.getLines(audience, Messages::help)));
        assertEquals("/auction bid\n/auction info", plain(lang.getLines((Locale) null, Messages::help)));
    }

    @Test
    void anEmptyListSendsNothing() throws IOException {
        write("en_US", "help: []\n");
        final LocaleHandle<Messages> lang = handle(true);
        final Capture audience = new Capture(null);

        lang.sendLines(audience, Messages::help);

        assertEquals(List.of(), audience.received);
        assertEquals(Component.empty(), lang.getLines(audience, Messages::help));
    }

    @Test
    void theTextServiceGivesEveryMessageItsTags() throws IOException {
        write("en_US", "outbid: <prefix>You were outbid!\n");
        final LocaleHandle<Messages> lang = LocaleHandle.open(new LocaleHandle<>(host,
                TextService.create().with(Placeholder.parsed("prefix", "[Auction] ")), lang(), "lang",
                Messages.class, Messages::defaults, Codecs.create(), 1, List.of(), Locale.US, List.of(), false));

        assertEquals("[Auction] You were outbid!", plain(lang.get((Locale) null, Messages::outbid)));
    }

    // --- reload ---------------------------------------------------------------------------------------------------

    @Test
    void reloadPicksUpEditsNewLanguagesAndRemovedFiles() throws IOException {
        write("th", "outbid: th\n");
        final LocaleHandle<Messages> lang = handle(true);
        write("th", "outbid: th edited\n");
        write("de_DE", "outbid: de\n");

        final FilesReloadResult first = lang.reload();

        assertTrue(first.changed());
        assertEquals("th edited", outbid(lang, Locale.of("th")));
        assertEquals("de", outbid(lang, Locale.GERMANY));

        Files.delete(lang().resolve("de_DE.yml"));
        final FilesReloadResult second = lang.reload();

        assertEquals(List.of("de_DE"), second.removed());
        assertEquals("You were outbid!", outbid(lang, Locale.GERMANY));
    }

    @Test
    void aBrokenFileKeepsItsMessagesAndTheOthersReload() throws IOException {
        write("th", "outbid: th\n");
        final LocaleHandle<Messages> lang = handle(true);
        write("th", "outbid: [unclosed\n");
        write("en_US", read("en_US").replace("You were outbid!", "Edited outbid"));

        final FilesReloadResult result = lang.reload();

        assertFalse(result.ok());
        assertEquals(1, result.files().get("th").errors().size());
        assertEquals("th", outbid(lang, Locale.of("th")), "the old messages stay");
        assertEquals("Edited outbid", outbid(lang, Locale.US));
        assertEquals("outbid: [unclosed\n", read("th"), "the broken file is left as it is");
    }

    @Test
    void aMissingDefaultFileIsCreatedAgainByReload() throws IOException {
        final LocaleHandle<Messages> lang = handle(true);
        Files.delete(lang().resolve("en_US.yml"));

        lang.reload();

        assertTrue(Files.exists(lang().resolve("en_US.yml")));
    }

    @Test
    void aPlainReloadChangesNothingAndLeavesTheFilesAlone() throws IOException {
        write("th", "outbid: th\nbid-placed: a\nhelp: [x]\ndisabled: ''\n");
        final LocaleHandle<Messages> lang = handle(true);
        final String english = read("en_US");
        final String thai = read("th");

        final FilesReloadResult result = lang.reload();

        assertFalse(result.changed());
        assertTrue(result.ok());
        assertEquals(english, read("en_US"));
        assertEquals(thai, read("th"));
    }

    @Test
    void otherYamlFilesInTheFolderAreNotTreatedAsLanguages() throws IOException {
        write("notes", "hello: world\n");
        write("Messages", "x: y\n");
        final LocaleHandle<Messages> lang = handle(true);

        assertEquals(List.of("en_US"), List.copyOf(lang.reload().files().keySet()));
        assertEquals("hello: world\n", read("notes"));
    }

    private LocaleHandle<Sectioned> sectioned(final String... bundled) {
        return LocaleHandle.open(new LocaleHandle<>(host, TextService.create(), lang(), "lang", Sectioned.class,
                Sectioned::defaults, Codecs.create(), 1, List.of(), Locale.US, List.of(bundled), true));
    }

    // --- map sections -----------------------------------------------------------------------------------------

    @Test
    void aKeyAddedToAMapSectionReachesAnOldFile() throws IOException {
        write("en_US", "config-version: 1\ntitle: Mine\ndialog:\n  sell-title: My sell\n  sell-note: My note\n");

        final LocaleHandle<Sectioned> lang = sectioned();

        final String text = read("en_US");
        assertTrue(text.contains("sell-error-too-high: Too high"), text);
        assertTrue(text.contains("sell-title: My sell") && text.contains("sell-note: My note"), text);
        assertEquals("Too high", plain(lang.get((Locale) null, m -> m.dialog().get("sell-error-too-high"))));
        assertEquals("My sell", plain(lang.get((Locale) null, m -> m.dialog().get("sell-title"))));
    }

    @Test
    void anEntryThatIsEmptyStaysEmpty() throws IOException {
        write("en_US", "config-version: 1\ntitle: Mine\ndialog:\n  sell-title: Sell\n  sell-error-too-high: ''\n"
                + "  sell-note: Note\n");

        final LocaleHandle<Sectioned> lang = sectioned();

        assertEquals("", plain(lang.get((Locale) null, m -> m.dialog().get("sell-error-too-high"))));
        assertTrue(read("en_US").contains("sell-error-too-high: ''"), read("en_US"));
    }

    @Test
    void aMissingEntryTakesTheBundledTextOfItsLanguageElseTheDefault() throws IOException {
        host.resources.put("lang/th_TH.yml", "title: ประมูล\ndialog:\n  sell-title: ขาย\n  sell-error-too-high: แพงไป\n");
        write("th_TH", "config-version: 1\ntitle: ประมูล\ndialog:\n  sell-title: ขาย\n");

        final LocaleHandle<Sectioned> lang = sectioned("th_TH");

        final Locale thai = Locale.of("th", "TH");
        assertEquals("แพงไป", plain(lang.get(thai, m -> m.dialog().get("sell-error-too-high"))));
        assertEquals("Note", plain(lang.get(thai, m -> m.dialog().get("sell-note"))), "not bundled: the default");
        assertEquals("ขาย", plain(lang.get(thai, m -> m.dialog().get("sell-title"))));
    }

    @Test
    void aFilledEntryIsWrittenOnceAndLaterReloadsLeaveTheFileAlone() throws IOException {
        write("en_US", "config-version: 1\ntitle: Mine\ndialog:\n  sell-title: Sell\n");
        final LocaleHandle<Sectioned> lang = sectioned();
        final String filled = read("en_US");

        final FilesReloadResult result = lang.reload();

        assertEquals(filled, read("en_US"));
        assertFalse(result.changed());
    }

    @Test
    void aRemovedMapSectionOfOtherConfigsIsStillTheUsersToRemove() {
        final Codecs codecs = Codecs.create();
        final var result = codecs.decodeLenient(Map.of("title", "T", "dialog", Map.of("sell-title", "x")),
                Sectioned.class, Sectioned.defaults());

        assertEquals(Map.of("sell-title", "x"), result.value().dialog());
        assertTrue(result.missing().isEmpty());
    }

    @Test
    void afterTheServiceIsClosedReloadFails() {
        final LocaleHandle<Messages> lang = handle(true);
        host.closed = true;

        assertThrows(IllegalStateException.class, lang::reload);
    }
}
