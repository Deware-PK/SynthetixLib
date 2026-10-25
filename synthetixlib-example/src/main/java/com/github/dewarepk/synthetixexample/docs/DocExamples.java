package com.github.dewarepk.synthetixexample.docs;

import com.github.dewarepk.synthetixexample.demo.Airdrop;
import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixlib.command.Arg;
import com.github.dewarepk.synthetixlib.command.Args;
import com.github.dewarepk.synthetixlib.command.CommandCall;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.Change;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.FilesReloadResult;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.config.ReloadResult;
import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.UUID;
import java.util.List;
import com.github.dewarepk.synthetixlib.gui.MenuType;
import com.github.dewarepk.synthetixlib.gui.GuiService;
import com.github.dewarepk.synthetixlib.dialog.DialogType;
import com.github.dewarepk.synthetixlib.dialog.DialogSettings;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.database.RowMapper;
import com.github.dewarepk.synthetixlib.database.Migrations;
import com.github.dewarepk.synthetixlib.database.Dialect;
import com.github.dewarepk.synthetixlib.database.DatabaseSettings;
import com.github.dewarepk.synthetixlib.database.Database;
import com.github.dewarepk.synthetixlib.database.Copy;
import com.github.dewarepk.synthetixlib.core.item.ItemBuilder;
import com.github.dewarepk.synthetixlib.core.pdc.PdcKey;
import com.github.dewarepk.synthetixlib.core.scheduler.Scheduler;
import com.github.dewarepk.synthetixlib.core.scheduler.SynthetixTask;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Small, compiling examples that the skill (skills/synthetixlib/references) copies with {@code @snippet} markers.
 * Nothing here runs at startup; every API used is covered by the core, config and command self-tests.
 */
final class DocExamples {

    private final SynthetixLib lib;

    DocExamples(final SynthetixLib lib) {
        this.lib = lib;
    }

    void scheduler(final Player player, final Location location, final Block block) {
        // @snippet:core-scheduler
        final Scheduler scheduler = lib.scheduler();

        scheduler.global().runLater(40, () -> lib.plugin().getLogger().info("two seconds later"));
        final SynthetixTask timer = scheduler.global().runTimer(0, 20, task -> {
            if (lib.isClosed()) {
                task.cancel();
            }
        });

        scheduler.region(location).run(() -> block.setType(Material.GOLD_BLOCK));
        scheduler.entity(player).run(() -> player.sendMessage("hi"), () -> lib.plugin().getLogger().info("player left"));

        scheduler.async().run(() -> lib.plugin().getLogger().info("off the server thread"));
        scheduler.async().runTimer(Duration.ZERO, Duration.ofMinutes(1), task -> lib.plugin().getLogger().info("autosave"));
        // @end
        timer.cancel();
    }

    void text(final Player player, final Component itemName, final String userInput) {
        // @snippet:core-text
        final TextService text = lib.text();

        text.send(player, "<green>Bid of <amount> on <item>",
                Placeholder.unparsed("amount", "100"),
                Placeholder.component("item", itemName));

        final TextService prefixed = text.with(Placeholder.parsed("prefix", "<dark_gray>[<gold>Shop</gold>]"));
        prefixed.send(player, "<prefix> <gray>Welcome");

        final String safe = text.escape(userInput);
        text.send(player, "<yellow>You typed: " + safe);
        text.send(player, "<yellow>You typed: <input>", Placeholder.unparsed("input", userInput));
        // @end
    }

    ItemStack items(final ItemStack existing, final PdcKey<String> lotId) {
        // @snippet:core-items
        final ItemBuilder ticket = lib.items().of(Material.PAPER)
                .name("<gold>Auction ticket")
                .lore("<gray>Lot #<id>", Placeholder.unparsed("id", "12"))
                .itemModel(new NamespacedKey(lib.plugin(), "auction_ticket"))
                .glint(true)
                .set(lotId, "12");
        final ItemStack first = ticket.build();
        final ItemStack second = ticket.amount(2).build();      // build() returns a fresh stack every time

        final ItemStack expired = lib.items().from(existing).clearLore().lore("<red>Expired").build();
        // @end
        return first.isEmpty() || second.isEmpty() ? expired : existing;
    }

    void pdc(final ItemStack stack, final Player player) {
        // @snippet:core-pdc
        final PdcKey<String> lotId = lib.keys().string("lot_id");
        final PdcKey<Integer> bids = lib.keys().integer("bids");

        lotId.set(stack, "13");
        final Optional<String> lot = lotId.get(stack);
        final boolean tagged = lotId.has(stack);
        bids.set(player, 3);                                    // any PersistentDataHolder
        lotId.remove(stack);
        // @end
        if (lot.isEmpty() && !tagged) {
            bids.remove(player);
        }
    }

    void collection(final ConfigCollection<Airdrop> airdrops, final Player player) {
        // @snippet:config-collection
        airdrops.create("spawn");                                         // IllegalStateException if it exists
        airdrops.update("spawn", drop -> drop.withName("Spawn drop"));    // rewrites only the changed keys
        final Optional<Airdrop> one = airdrops.get("spawn");
        final Map<String, Airdrop> all = airdrops.all();                  // immutable snapshot
        airdrops.rename("spawn", "hub");
        final boolean deleted = airdrops.delete("hub");                   // moved to airdrops/.deleted/

        airdrops.onChange(change -> {
            switch (change) {
                case Change.Added<Airdrop> added -> lib.plugin().getLogger().info("added " + added.id());
                case Change.Updated<Airdrop> updated -> lib.plugin().getLogger().info("updated " + updated.id());
                case Change.Removed<Airdrop> removed -> lib.plugin().getLogger().info("removed " + removed.id());
            }
        });
        final FilesReloadResult result = airdrops.reload();               // a broken file is skipped, the rest load
        result.files().forEach((file, outcome) -> outcome.errors().forEach(lib.plugin().getLogger()::warning));
        // @end
        if (one.isPresent() && all.isEmpty() && deleted) {
            player.sendMessage("unreachable");
        }
    }

    void migration(final Configs configs) {
        // @snippet:config-migration
        final ConfigHandle<Airdrop> handle = configs.file("airdrop.yml", Airdrop.class, Airdrop::standard)
                .version(2)
                .migrate(1, 2, tree -> tree.rename("min-players", "min-online"))
                .load();
        // @end
        handle.onReload(snapshot -> lib.plugin().getLogger().info("reloaded " + snapshot.name()));
    }

    /** A value type that is stored as one string. */
    record Money(long cents) {
        static Money parse(final String text) {
            return new Money(Long.parseLong(text.replace(".", "")));
        }

        String format() {
            return cents / 100 + "." + String.format("%02d", cents % 100);
        }
    }

    void codecs(final Configs configs) {
        // @snippet:config-codec
        final Codecs codecs = configs.codecs();
        codecs.register(Money.class, Codec.string(Money::parse, Money::format));

        final Object tree = codecs.encode(new Money(1250));              // "12.50"
        final Money back = codecs.decode(tree, Money.class);              // DecodeException (with path) if invalid
        final String json = codecs.toJson(Airdrop.standard());
        final Airdrop airdrop = codecs.fromJson(json, Airdrop.class);
        // @end
        try {
            codecs.decode(back, Airdrop.class);
        } catch (final DecodeException invalid) {
            invalid.problems().forEach(problem -> lib.plugin().getLogger().warning(problem.toString()));
        }
        lib.plugin().getLogger().info(airdrop.name());
    }

    void locale(final LocaleHandle<Messages> lang, final Player player) {
        // @snippet:config-locale-send
        lang.send(player, Messages::welcome, Placeholder.unparsed("name", player.getName()));
        lang.sendLines(player, Messages::help);                           // List<String>: one chat line each
        final Component line = lang.get(player, Messages::reloaded);      // when you need the Component
        final Component forConsole = lang.get(lang.defaultLocale(), Messages::reloaded);
        // @end
        player.sendMessage(line);
        player.sendMessage(forConsole);
    }

    void reload(final ConfigHandle<Airdrop> handle) {
        // @snippet:config-reload-result
        final ReloadResult result = handle.reload();                      // never rewrites the file
        result.errors().forEach(error -> lib.plugin().getLogger().warning(error));
        handle.reloadAsync().thenAccept(async -> lib.plugin().getLogger().info("changed: " + async.changed()));
        // @end
    }

    /** {@code /example ban <player> [time]}: the database work runs off the server thread. */
    final class BanCommand implements SubCommand {

        private final Arg<Player> target = Arg.of("player", Args.player());
        private final Arg<Duration> time = Arg.of("time", Args.duration());
        private final LocaleHandle<Messages> lang;

        BanCommand(final LocaleHandle<Messages> lang) {
            this.lang = lang;
        }

        // @snippet:command-async
        @Override
        public SubSpec spec() {
            return SubSpec.literal("ban").argument(target).optional(time, Duration.ofDays(1))
                    .usage("<player> [time]").description("Ban a player")
                    .executesAsync(this::run);
        }

        private void run(final CommandCall ctx) {
            final Player player = ctx.get(target);
            final Duration length = ctx.getOrDefault(time, Duration.ofDays(1));
            storeBan(player, length);                                     // blocking is fine here
            ctx.runOnSender(() -> ctx.reply(lang, Messages::reloaded, Placeholder.component("player", player.name())));
        }
        // @end

        private void storeBan(final Player player, final Duration length) {
            lib.plugin().getLogger().info(player.getName() + " " + length);
        }
    }

    // ---- gui, dialog, database -------------------------------------------------------------------------------

    private List<String> loadHistory(final Player player) {
        return List.of(player.getName());
    }

    void gui(final GuiService gui, final DialogService dialogs, final Player player) {
        // @snippet:gui-async-input
        final MenuType<Void> history = gui.menu("history")
                .listAsync("entries", view -> loadHistory(view.player()),          // slow source: runs async
                        (entry, view) -> lib.items().of(Material.PAPER).name("<white>" + lib.text().escape(entry)).build())
                .onInputChange('S', (view, items) -> view.refresh('C'))             // an input slot changed
                .action("next", click -> click.view().page(click.view().page() + 1))
                .action("leave", click -> {
                    click.view().back();                                            // previous menu of this viewer
                    click.view().close();
                })
                .build();
        history.open(player);                                                       // any thread
        // @end

        // @snippet:gui-links
        gui.link("dialog", dialogs.opener());              // menu files may now say: click: ['dialog:sell']
        dialogs.link("menu", gui.opener());                // dialog files may now say: click: ['menu:history']
        final FilesReloadResult reloaded = gui.reload();   // re-reads every menu file; open views redraw on refresh
        // @end
        reloaded.errors().forEach(lib.plugin().getLogger()::warning);
    }

    /** The answers of a form, read asynchronously. */
    record SearchForm(String query, boolean exact) {
    }

    void dialog(final DialogService dialogs, final Player player) {
        // @snippet:dialog-more
        final DialogType<String> search = dialogs.dialog("search").<String>withContext()
                .input("query", view -> view.context())                              // initial value from code
                .action("clear", click -> click.view().reopen())                     // a button without a form
                .submitAsync("search", SearchForm.class, (form, click) -> {          // slow: runs async
                    final String found = form.query() + (form.exact() ? " (exact)" : "");
                    click.view().player().sendMessage(found);
                })
                .onClose(view -> lib.plugin().getLogger().info(view.player().getName() + " closed it"))
                .build();
        search.open(player, "last query");                                           // any thread
        // @end
    }

    void dialogService(final DialogService dialogs) {
        // @snippet:dialog-service-options
        dialogs.settings(new DialogSettings(Duration.ofMinutes(2), false))           // how long the buttons work
                .fallback(player -> player.getProtocolVersion() < 771,               // clients that cannot show dialogs
                        (player, view) -> player.sendMessage("Please update your game"));
        // @end
    }

    /** What the price dialog shows: what the player typed and the error line ({@code <error>} in the file). */
    record PriceAsk(String typed, String error) {
    }

    private DialogType<PriceAsk> priceDialog;

    void dialogLimits(final DialogService dialogs, final long maxPrice, final Player player) {
        // @snippet:dialog-config-limits
        priceDialog = dialogs.dialog("price").<PriceAsk>withContext()
                .placeholder("error", view -> Component.text(view.context().error()))   // "" or the message
                .input("price", view -> view.context().typed())                         // text input: any size
                .action("confirm", click -> {
                    final String typed = click.response().text("price").orElse("").trim();
                    long price = -1;
                    try {
                        price = Long.parseLong(typed);
                    } catch (final NumberFormatException ignored) {
                        // stays -1: the same error as a price out of range
                    }
                    if (price < 1 || price > maxPrice) {                                // limit from config.yml
                        click.view().next(priceDialog, new PriceAsk(typed, "Price must be 1-" + maxPrice));
                        return;
                    }
                    click.player().sendMessage("Price: " + price);
                })
                .build();
        priceDialog.open(player, new PriceAsk("", ""));
        // @end
    }

    // @snippet:command-own-cooldown
    /** A cooldown the command module cannot express: a reloadable length and a reset for one player. */
    final class KitCooldowns implements Listener {

        private final Map<UUID, Long> readyAt = new ConcurrentHashMap<>();   // instance field, never static

        /** @return the seconds left, 0 if the player may use the kit now (and starts the wait if so) */
        long tryUse(final UUID player, final Duration length) {
            final long now = System.currentTimeMillis();
            final Long ready = readyAt.get(player);
            if (ready != null && ready > now) {
                return (ready - now + 999) / 1000;
            }
            readyAt.put(player, now + length.toMillis());                    // length read per use: reload works
            return 0;
        }

        void reset(final UUID player) {
            readyAt.remove(player);                                          // e.g. /kit reset <player>
        }

        @EventHandler
        void onQuit(final PlayerQuitEvent event) {
            readyAt.remove(event.getPlayer().getUniqueId());                 // clean up on quit
        }
    }
    // @end

    void database(final Database db, final Player player, final UUID seller, final long price) {
        // @snippet:db-statements
        final CompletableFuture<List<UUID>> sellers =
                db.query("SELECT seller FROM {prefix}lots WHERE price > ?", row -> row.uuid("seller"), price);
        final CompletableFuture<Optional<Long>> one =
                db.queryOne("SELECT price FROM {prefix}lots WHERE seller = ?", row -> row.longValue("price"), seller);
        final CompletableFuture<Integer> changed =
                db.update("UPDATE {prefix}lots SET price = ? WHERE seller = ?", price, seller);

        db.transaction(tx -> {                                                       // all or nothing
            tx.update("UPDATE {prefix}balances SET money = money - ? WHERE player = ?", price, player.getUniqueId());
            tx.update("INSERT INTO {prefix}mail (player, note) VALUES (?, ?)", seller, "sold");
            return null;
        });

        db.batch("INSERT INTO {prefix}lots (seller, price) VALUES (?, ?)",
                List.of(new Object[]{seller, 10L}, new Object[]{seller, 20L}));
        db.upsert("{prefix}balances", List.of("player"), Map.of("player", seller, "money", 100L));

        // back on the player's own thread before touching Bukkit
        sellers.thenAccept(list -> lib.scheduler().entity(player).run(() -> player.sendMessage("sellers: " + list.size())));
        // @end
        if (one.isDone() && changed.isDone()) {
            lib.plugin().getLogger().info(db.dialect().name());
        }
    }

    void migrations(final Database db) {
        // @snippet:db-migrations
        final Migrations migrations = Migrations.fromResources("db/migrations")      // V1__init.sql, V2__mail.sql, ...
                .withCode(3, "import-v1", tx -> tx.update("UPDATE {prefix}lots SET price = price * 100"));
        db.migrate(migrations).thenAccept(applied -> lib.plugin().getLogger().info("migrated"));
        // @end
    }

    void copy(final Database current, final DatabaseSettings legacy) {
        // @snippet:db-copy
        final Database old = Database.open(lib, legacy);                              // not attached to lib: close it yourself
        Copy.tables(old, current, List.of("lots", "mail")).batchSize(500).run()
                .whenComplete((report, failure) -> {
                    old.close();
                    if (failure == null) {
                        lib.plugin().getLogger().info("copied " + report.total() + " rows");
                    }
                });
        // @end
    }

    void rows(final Database db) {
        // @snippet:db-row-mapper
        final RowMapper<String> mapper = row -> row.string("name") + " " + row.intValueOrNull("level")
                + " " + row.instant("created_at") + " " + row.enumOf("kind", Dialect.class);
        // @end
        db.query("SELECT name, level, created_at, kind FROM {prefix}things", mapper);
    }
}
