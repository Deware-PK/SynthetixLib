package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixexample.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.FilesReloadResult;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.dialog.DialogType;
import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import com.github.dewarepk.synthetixlib.dialog.file.DialogBody;
import com.github.dewarepk.synthetixlib.dialog.file.DialogFile;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import com.github.dewarepk.synthetixlib.dialog.file.DialogKind;
import com.github.dewarepk.synthetixlib.dialog.DialogView;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-test for the dialog module: dialog files copied from the jar, parsed with the real Bukkit codecs, checked. */
final class DialogSelfTests {

    /** The form of the bundled dialog; public so the config codecs can fill it. */
    public record GoForm(double price, String duration, String note, boolean anonymous) {
    }

    /** The form of selftest-submit.yml; public so the config codecs can fill it. */
    public record SubmitForm(double price, String myKey, String duration, boolean anonymous) {
    }

    private DialogSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        final Configs configs = Configs.create(lib);
        final DialogService dialogs = DialogService.create(lib, configs);
        final AtomicReference<DialogType<Void>> files = new AtomicReference<>();
        final SubmitBed submitBed = new SubmitBed();
        // the fake players of dialog.callback are not entities: their tasks run right away, on this (server) thread
        dialogs.executor((player, task) -> task.run());
        return List.of(
                SelfTest.sync("dialog.files", () -> {
                    final Path file = plugin.getDataFolder().toPath().resolve("dialogs/selftest-dialog.yml");
                    Files.deleteIfExists(file);
                    final String bundled;
                    try (InputStream in = plugin.getResource("dialogs/selftest-dialog.yml")) {
                        require(in != null, "dialogs/selftest-dialog.yml is not in the plugin jar");
                        bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }

                    final DialogType<Void> type = dialogs.dialog("selftest-dialog")
                            .placeholder("item-name", view -> Component.text("x"))
                            .submit("go", GoForm.class, (form, click) -> { })
                            .build();
                    files.set(type);
                    require(Files.exists(file), "the bundled file was not copied");
                    require(Files.readString(file, StandardCharsets.UTF_8).equals(bundled), "the copy differs from the jar");
                    require(Files.readString(file, StandardCharsets.UTF_8).startsWith("# Bundled"),
                            "the comments of the bundled file were lost");

                    final DialogFile parsed = type.file();
                    require(parsed.kind() == DialogKind.CONFIRMATION, "kind " + parsed.kind());
                    require(!parsed.canCloseWithEscape(), "can-close-with-escape");
                    require(parsed.body().size() == 3, "body " + parsed.body());
                    final DialogBody.Item prize = (DialogBody.Item) parsed.body().get(2);
                    require(prize.item().material().orElse(null) == Material.DIAMOND, "material " + prize.item());
                    require(NamespacedKey.minecraft("diamond").equals(prize.item().itemModel().orElse(null)),
                            "item-model " + prize.item().itemModel());
                    require(parsed.inputs().keySet().equals(java.util.Set.of("price", "duration", "note", "anonymous"))
                            && List.copyOf(parsed.inputs().keySet()).get(0).equals("price"), "inputs " + parsed.inputs().keySet());
                    final DialogInput.NumberInput price = (DialogInput.NumberInput) parsed.inputs().get("price");
                    require(price.min() == 1 && price.max() == 1000, "price " + price);
                    require(parsed.buttons().named().get("yes").clicks().actions()
                            .equals(List.of(new ClickAction.Run("go"), new ClickAction.PlaySound(
                                    NamespacedKey.minecraft("ui.button.click")))), "yes clicks");

                    require(!type.isInert("buttons.yes"), "the yes button must work");
                    require(type.isInert("buttons.no"), "the no button uses action:nope, which nobody registered");
                    final FilesReloadResult reload = dialogs.reload();
                    require(reload.ok(), "reload errors: " + reload.errors());
                    require(reload.warnings().stream().anyMatch(w -> w.contains("action 'nope' is not registered")),
                            "the unknown action was not reported: " + reload.warnings());
                    require(!reload.changed(), "an unchanged file must not report a change");
                    require(Files.readString(file, StandardCharsets.UTF_8).equals(bundled),
                            "a plain reload must not rewrite the file");
                }),
                SelfTest.sync("dialog.render", () -> render(plugin, dialogs, files.get())),
                SelfTest.sync("dialog.callback", () -> callback(plugin, lib, configs, dialogs)),
                SelfTest.sync("dialog.fallback", () -> fallback(plugin, dialogs)),
                SelfTest.sync("dialog.submit", () -> submit(plugin, dialogs, submitBed)),
                SelfTest.async("dialog.submit-async", () -> submitAsync(submitBed)),
                SelfTest.sync("dialog.limits", () -> limits(plugin)));
    }

    /** What dialog.submit-async needs from dialog.submit: the dialog and the places its handlers write to. */
    private record SubmitBed(AtomicReference<DialogType<Void>> type, List<SubmitForm> forms,
                             List<SubmitForm> asyncForms, AtomicReference<Boolean> asyncOnServerThread) {
        SubmitBed() {
            this(new AtomicReference<>(), new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>(),
                    new AtomicReference<>());
        }
    }

    /**
     * Typed answers through the real callback: a valid form reaches the handler, a refused one shows the dialog again
     * (fresh buttons, typed values) without calling it. The key {@code my-key} of the file is {@code my_key} on the wire.
     */
    private static void submit(final JavaPlugin plugin, final DialogService dialogs, final SubmitBed bed)
            throws Exception {
        Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("dialogs/selftest-submit.yml"));
        final DialogType<Void> type = dialogs.dialog("selftest-submit")
                .submit("send", SubmitForm.class, (form, click) -> bed.forms().add(form))
                .submitAsync("send-async", SubmitForm.class, (form, click) -> {
                    bed.asyncOnServerThread().set(Bukkit.isPrimaryThread());
                    bed.asyncForms().add(form);
                })
                .build();
        bed.type().set(type);
        require(!type.isInert("buttons.yes") && !type.isInert("buttons.no"),
                "the form fits the inputs: yes " + type.isInert("buttons.yes") + ", no " + type.isInert("buttons.no"));

        final FakePlayer valid = new FakePlayer("Valid", Locale.US);
        final DialogView<Void> view = type.attach(valid.player, null);
        type.click(view, "buttons.yes", new Fake(Map.of("price", 12f, "my_key", "abc", "duration", "h1",
                "anonymous", true)), valid.player);
        require(bed.forms().equals(List.of(new SubmitForm(12, "abc", "h1", true))), "the form: " + bed.forms());
        require(valid.closed == 1 && valid.shown == 0, "a valid form ends with the dialog closed");

        // the file key is sent without the dash: an answer under 'my-key' is no answer
        final FakePlayer dash = new FakePlayer("Dash", Locale.US);
        final DialogView<Void> dashView = type.attach(dash.player, null);
        type.click(dashView, "buttons.yes", new Fake(Map.of("price", 12f, "my-key", "abc", "duration", "h1",
                "anonymous", true)), dash.player);
        require(bed.forms().size() == 1 && dash.shown == 1 && dash.closed == 0,
                "an answer under the file key must be refused: forms " + bed.forms() + ", shown " + dash.shown);

        final Map<String, Object> good = Map.of("price", 12f, "my_key", "abc", "duration", "h1", "anonymous", false);
        for (final Map.Entry<String, Object> bad : Map.<String, Object>of("price", 500f, "my_key", "123456789",
                "duration", "h99").entrySet()) {
            final FakePlayer refused = new FakePlayer("Refused-" + bad.getKey(), Locale.US);
            final DialogView<Void> refusedView = type.attach(refused.player, null);
            final Map<String, Object> answers = new java.util.HashMap<>(good);
            answers.put(bad.getKey(), bad.getValue());
            type.click(refusedView, "buttons.yes", new Fake(answers), refused.player);
            require(bed.forms().size() == 1, "'" + bad.getKey() + "' = " + bad.getValue() + " reached the handler");
            require(refused.shown == 1 && refused.closed == 0, "the dialog is shown again: shown " + refused.shown);
            // the new showing has fresh buttons: the same view submits a corrected form
            type.click(refusedView, "buttons.yes", new Fake(good), refused.player);
            require(bed.forms().size() == 2 && refused.closed == 1, "after the error the corrected form works: "
                    + bed.forms());
            bed.forms().remove(1);
        }
        final FakePlayer offStep = new FakePlayer("OffStep", Locale.US);
        type.click(type.attach(offStep.player, null), "buttons.yes", new Fake(Map.of("price", 12.5f, "my_key", "a",
                "duration", "h1", "anonymous", true)), offStep.player);
        require(bed.forms().size() == 1 && offStep.shown == 1, "a value off the step reached the handler");
    }

    /** {@code submitAsync}: the handler is not on the server thread and the dialog closes when it is over. */
    private static CompletableFuture<?> submitAsync(final SubmitBed bed) {
        final DialogType<Void> type = bed.type().get();
        require(type != null, "dialog.submit did not run");
        final FakePlayer player = new FakePlayer("Async", Locale.US);
        final DialogView<Void> view = type.attach(player.player, null);
        type.click(view, "buttons.no", new Fake(Map.of("price", 7f, "my_key", "z", "duration", "h12",
                "anonymous", false)), player.player);
        return CompletableFuture.runAsync(() -> {
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (player.closed == 0 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            require(bed.asyncForms().equals(List.of(new SubmitForm(7, "z", "h12", false))),
                    "the async form: " + bed.asyncForms());
            require(Boolean.FALSE.equals(bed.asyncOnServerThread().get()),
                    "the async handler ran on the server thread: " + bed.asyncOnServerThread().get());
            require(player.closed == 1, "the dialog is closed when the async handler is over: " + player.closed);
        });
    }

    /**
     * What Paper accepts, written to the log for the task result: the size of an item body, a {@code -} in an input
     * key. Fails if the module's own limits (256, keys with {@code _}) disagree with Paper.
     */
    private static void limits(final JavaPlugin plugin) {
        final ItemStack stack = new ItemStack(Material.DIAMOND);
        require(accepts(() -> io.papermc.paper.registry.data.dialog.body.DialogBody.item(stack).width(256).height(256)
                .build()), "Paper refuses an item body of 256 x 256, the module allows it");
        final boolean width257 = accepts(() -> io.papermc.paper.registry.data.dialog.body.DialogBody.item(stack)
                .width(257).build());
        final boolean height257 = accepts(() -> io.papermc.paper.registry.data.dialog.body.DialogBody.item(stack)
                .height(257).build());
        final boolean dash = accepts(() -> dialogWithKey("my-key"));
        final boolean underscore = accepts(() -> dialogWithKey("my_key"));
        plugin.getLogger().info("dialog.limits: Paper accepts item body width 257: " + width257 + ", height 257: "
                + height257 + "; input key 'my-key': " + dash + ", 'my_key': " + underscore);
        require(!width257 && !height257, "Paper accepts an item body above 256, the module clamps it to 256");
        require(underscore, "Paper refuses an input key with an underscore");
    }

    private static void dialogWithKey(final String key) {
        final var input = io.papermc.paper.registry.data.dialog.input.DialogInput.bool(key, Component.text("x")).build();
        Dialog.create(factory -> factory.empty().base(io.papermc.paper.registry.data.dialog.DialogBase
                .builder(Component.text("t")).inputs(List.of(input)).build())
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.notice()));
    }

    private static boolean accepts(final Runnable build) {
        try {
            build.run();
            return true;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** Every kind of dialog, body entry and input is accepted by the server's dialog API, with placeholders resolved. */
    private static void render(final JavaPlugin plugin, final DialogService dialogs, final DialogType<Void> form)
            throws Exception {
        for (final String id : List.of("selftest-notice", "selftest-multi", "selftest-list", "selftest-links")) {
            Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("dialogs/" + id + ".yml"));
        }
        final AtomicInteger asked = new AtomicInteger();
        final Function<DialogView<Void>, Component> itemName = view -> {
            asked.incrementAndGet();
            return Component.text("Sword");
        };
        final DialogType<Void> notice = dialogs.dialog("selftest-notice")
                .placeholder("item-name", itemName)
                .item("lot-item", view -> new ItemStack(Material.DIAMOND_SWORD))
                .build();
        final DialogType<Void> multi = dialogs.dialog("selftest-multi").action("count", click -> { }).build();
        final DialogType<Void> links = dialogs.dialog("selftest-links").build();
        final DialogType<Void> list = dialogs.dialog("selftest-list").build();
        require(notice.file().kind() == DialogKind.NOTICE && multi.file().kind() == DialogKind.MULTI_ACTION
                && list.file().kind() == DialogKind.LIST && links.file().kind() == DialogKind.LINKS, "kinds");

        for (final DialogType<Void> type : List.of(notice, multi, list, links, form)) {
            final Dialog built = type.preview("Bot", null);
            require(built != null, type.id() + " did not render");
        }
        require(asked.get() >= 1, "the <item-name> placeholder was not asked while rendering: " + asked.get());
        require(!notice.isInert("buttons.ok"), "the ok button must work");
    }

    /**
     * The click path of a button, through the same callback object that the dialog holds: the first click runs the
     * steps and the handler once and ends with the dialog closed, a second click and a click from somebody else do
     * nothing, {@code reopen()} gives fresh buttons, texts use the viewer's language. Which thread Paper fires the
     * callback on cannot be measured without a client: the library hops to the viewer's thread whatever it is.
     */
    private static void callback(final JavaPlugin plugin, final SynthetixLib lib, final Configs configs,
                                 final DialogService dialogs) throws Exception {
        final Path data = plugin.getDataFolder().toPath();
        Files.deleteIfExists(data.resolve("dialogs/selftest-click.yml"));
        deleteTree(data.resolve("selftest-dialog-lang"));
        final LocaleHandle<Texts> lang = configs.locale("selftest-dialog-lang", Texts.class, Texts::defaults)
                .defaultLocale(Locale.US)
                .bundled("th_TH")
                .perPlayer(true)
                .load();
        dialogs.messages(lang, Texts::dialog);

        final AtomicInteger runs = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final List<Boolean> onServerThread = new ArrayList<>();
        final DialogType<Void> type = dialogs.dialog("selftest-click")
                .action("count", click -> {
                    onServerThread.add(Bukkit.isPrimaryThread());
                    if (runs.incrementAndGet() == 2) {
                        click.view().reopen();
                    }
                })
                .onClose(view -> closes.incrementAndGet())
                .build();

        final FakePlayer english = new FakePlayer("Bot", Locale.US);
        final DialogView<Void> view = type.attach(english.player, null);
        final Fake answers = new Fake(Map.of("note", "hi", "price", 5f, "anon", true));

        type.click(view, "buttons.yes", answers, english.player);
        require(runs.get() == 1, "the handler ran " + runs.get() + " times");
        require(english.closed == 1 && english.shown == 0, "the dialog is closed after the click: closed " + english.closed);
        require(english.commands.equals(List.of("say hi")), "the command step: " + english.commands);
        require(english.sounds == 1, "the sound step: " + english.sounds);
        require(english.messages.equals(List.of("Hello Bot")), "<lang:hello> <player> in a message step: "
                + english.messages);
        require(closes.get() == 0, "onClose is for exit buttons only");

        type.click(view, "buttons.yes", answers, english.player);
        require(runs.get() == 1, "a second click of the same showing ran the handler again");

        final FakePlayer intruder = new FakePlayer("Intruder", Locale.US);
        final DialogView<Void> second = type.attach(english.player, null);
        type.click(second, "buttons.yes", answers, intruder.player);
        require(runs.get() == 1 && english.closed == 1, "a click from somebody else must do nothing");

        type.click(second, "buttons.yes", answers, english.player);      // 2nd run: the handler asks for reopen()
        require(runs.get() == 2 && english.shown == 1 && english.closed == 1,
                "reopen() shows the dialog again instead of closing: shown " + english.shown + ", closed " + english.closed);
        type.click(second, "buttons.yes", answers, english.player);      // the new showing has fresh buttons
        require(runs.get() == 3, "after reopen() the new buttons work: " + runs.get());

        final DialogView<Void> third = type.attach(english.player, null);
        type.click(third, "buttons.no", answers, english.player);
        require(closes.get() == 1, "the no button (only close) is an exit button: onClose ran " + closes.get());

        final FakePlayer thai = new FakePlayer("Thai", Locale.of("th", "TH"));
        final DialogView<Void> thaiView = type.attach(thai.player, null);
        type.click(thaiView, "buttons.yes", answers, thai.player);
        require(thai.messages.equals(List.of("Sawasdee Thai")), "the viewer's own language: " + thai.messages);

        require(!onServerThread.isEmpty() && onServerThread.stream().allMatch(Boolean::booleanValue),
                "handlers must run on the viewer's (server) thread: " + onServerThread);
    }

    /** A viewer whose client cannot show dialogs is handed to the fallback and nothing is shown. */
    private static void fallback(final JavaPlugin plugin, final DialogService dialogs) throws Exception {
        Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("dialogs/selftest-fallback.yml"));
        final DialogType<Void> type = dialogs.dialog("selftest-fallback").build();
        final FakePlayer old = new FakePlayer("Old", Locale.US);
        final FakePlayer modern = new FakePlayer("Modern", Locale.US);
        final List<String> handled = new ArrayList<>();
        dialogs.fallback(player -> player == old.player, (player, view) -> handled.add(player.getName()));

        type.open(old.player);
        type.open(modern.player);
        require(handled.equals(List.of("Old")), "the fallback got " + handled);
        require(old.shown == 0, "a viewer who cannot see dialogs is shown nothing");
        require(modern.shown == 1, "everybody else is shown the dialog: " + modern.shown);
    }

    /** What a client sends back: the answers by input key. */
    private record Fake(Map<String, Object> values) implements DialogResponseView {

        @Override
        public BinaryTagHolder payload() {
            return null;
        }

        @Override
        public String getText(final String key) {
            return values.get(key) instanceof String text ? text : null;
        }

        @Override
        public Boolean getBoolean(final String key) {
            return values.get(key) instanceof Boolean bool ? bool : null;
        }

        @Override
        public Float getFloat(final String key) {
            return values.get(key) instanceof Float number ? number : null;
        }
    }

    /** A player that answers only what the dialog module asks and records what it was told. */
    private static final class FakePlayer {
        final UUID id = UUID.randomUUID();
        final Player player;
        final List<String> messages = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        int shown;
        volatile int closed;
        int sounds;

        FakePlayer(final String name, final Locale locale) {
            final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
                    DialogSelfTests.class.getClassLoader(), new Class<?>[] {PlayerInventory.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getItemInMainHand", "getItemInOffHand" -> ItemStack.empty();
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException("PlayerInventory." + method.getName());
                    });
            this.player = (Player) Proxy.newProxyInstance(DialogSelfTests.class.getClassLoader(),
                    new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> name;
                        case "locale" -> locale;
                        case "getInventory" -> inventory;
                        case "showDialog" -> {
                            shown++;
                            yield null;
                        }
                        case "closeDialog" -> {
                            closed++;
                            yield null;
                        }
                        case "performCommand" -> {
                            commands.add((String) args[0]);
                            yield true;
                        }
                        case "sendMessage" -> {
                            if (args[0] instanceof Component component) {
                                messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                            }
                            yield null;
                        }
                        case "playSound" -> {
                            sounds++;
                            yield null;
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "FakePlayer " + name;
                        default -> throw new UnsupportedOperationException("Player." + method.getName());
                    });
        }
    }

    /** The language files of dialog.callback: the keys of {@code <lang:key>} in dialog files. */
    public record Texts(Map<String, String> dialog) {

        static Texts defaults() {
            return new Texts(Map.of("hello", "Hello <player>"));
        }
    }

    private static void deleteTree(final Path folder) throws java.io.IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (final Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
