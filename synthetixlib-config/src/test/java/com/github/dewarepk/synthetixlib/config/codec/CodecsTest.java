package com.github.dewarepk.synthetixlib.config.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.codec.Fixtures.Airdrop;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.AntiSnipe;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.AuctionConfig;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.CommandReward;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.Everything;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.ItemReward;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.Mode;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.MoneyReward;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.Node;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.Page;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.Reward;
import com.github.dewarepk.synthetixlib.config.codec.Fixtures.Zone;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodecsTest {

    private Codecs codecs;

    @BeforeEach
    void setUp() {
        codecs = Codecs.create();
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    /** The guarantee: decode(encode(x)) equals x, through the tree and through JSON. */
    private <T> void assertRoundTrip(final T value, final Class<T> type) {
        assertEquals(value, codecs.decode(codecs.encode(value, type), type), "tree round trip of " + value);
        assertEquals(value, codecs.fromJson(codecs.toJson(value, type), type), "JSON round trip of " + value);
    }

    private <T> void assertRoundTrip(final T value, final TypeToken<T> type) {
        assertEquals(value, codecs.decode(codecs.encode(value, type), type), "tree round trip of " + value);
        assertEquals(value, codecs.fromJson(codecs.toJson(value, type), type), "JSON round trip of " + value);
    }

    private List<String> problemsOf(final Runnable decode) {
        final DecodeException e = assertThrows(DecodeException.class, decode::run);
        return e.problems().stream().map(Problem::toString).toList();
    }

    private static Map<String, Object> map(final Object... keyValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> validZone() {
        return map("world", "world", "x", 0, "y", 64, "z", 0, "radius", 8);
    }

    private static Map<String, Object> validConfigTree() {
        return map("min-bid", 100, "bid-time", "1m", "anti-snipe", map("enabled", true, "extend-seconds", 10),
                "blacklist", List.of("BEDROCK"), "zones", map("spawn", validZone()));
    }

    // --- built-ins ---------------------------------------------------------------------------------------------

    @Test
    void builtInsRoundTrip() {
        assertRoundTrip(true, Boolean.class);
        assertRoundTrip(false, Boolean.class);
        assertRoundTrip(42, Integer.class);
        assertRoundTrip(Integer.MIN_VALUE, Integer.class);
        assertRoundTrip(Long.MAX_VALUE, Long.class);
        assertRoundTrip(-1.25, Double.class);
        assertRoundTrip(Double.MAX_VALUE, Double.class);
        assertRoundTrip(0.1f, Float.class);
        assertRoundTrip(Float.MAX_VALUE, Float.class);
        assertRoundTrip("hello \"world\" \n å", String.class);
        assertRoundTrip("", String.class);
        assertRoundTrip(Mode.DARK_OAK, Mode.class);
        assertRoundTrip(Duration.ofMillis(250), Duration.class);
        assertRoundTrip(Duration.ofHours(1).plusMinutes(30), Duration.class);
        assertRoundTrip(Duration.ofNanos(1_234_567), Duration.class);
        assertRoundTrip(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), UUID.class);
        assertRoundTrip(new NamespacedKey("synthetix", "my_key/sub"), NamespacedKey.class);
        assertRoundTrip(Key.key("synthetix", "thing.one"), Key.class);
    }

    @Test
    void primitiveClassesWork() {
        assertEquals(5, codecs.decode(5, int.class));
        assertEquals(5, codecs.decode(codecs.encode(5, int.class), int.class));
        assertEquals(true, codecs.decode(true, boolean.class));
    }

    @Test
    void integersAreRangeChecked() {
        assertEquals(List.of("out of range for int (-2147483648 to 2147483647, was 3000000000)"),
                problemsOf(() -> codecs.decode(3_000_000_000L, int.class)));
        assertEquals(List.of("out of range for long (was 99999999999999999999)"),
                problemsOf(() -> codecs.decode(new java.math.BigInteger("99999999999999999999"), long.class)));
        assertEquals(List.of("expected an integer, got number 1.5"), problemsOf(() -> codecs.decode(1.5, int.class)));
        assertEquals(List.of("expected an integer, got string \"5\""),
                problemsOf(() -> codecs.decode("5", int.class)));
        assertEquals(List.of("expected an integer, got boolean true"),
                problemsOf(() -> codecs.decode(true, int.class)));
        assertEquals(5, codecs.decode(5.0, int.class), "whole floating point numbers are accepted");
        assertEquals(7L, codecs.decode(7, long.class));
        assertEquals(2.0, codecs.decode(2, double.class));
        assertEquals(List.of("out of range for float (was 1.0E300)"),
                problemsOf(() -> codecs.decode(1e300, float.class)));
    }

    @Test
    void booleansAreStrict() {
        assertEquals(List.of("expected a boolean (true or false), got string \"yes\""),
                problemsOf(() -> codecs.decode("yes", boolean.class)));
    }

    @Test
    void stringsAcceptScalars() {
        assertEquals("12", codecs.decode(12, String.class));
        assertEquals("true", codecs.decode(true, String.class));
        assertEquals(List.of("expected a string, got a list"), problemsOf(() -> codecs.decode(List.of(), String.class)));
    }

    @Test
    void uuidAndKeysAreValidated() {
        assertEquals(List.of("invalid UUID \"1-1-1-1-1\" (expected xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)"),
                problemsOf(() -> codecs.decode("1-1-1-1-1", UUID.class)));
        assertEquals(1, problemsOf(() -> codecs.decode("Not A Key", NamespacedKey.class)).size());
        assertEquals(1, problemsOf(() -> codecs.decode("Not A Key", Key.class)).size());
        assertEquals(new NamespacedKey("minecraft", "stone"), codecs.decode("stone", NamespacedKey.class));
    }

    @Test
    void durationErrorsAreReported() {
        final List<String> problems = problemsOf(() -> codecs.decode("5mins", Duration.class));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("unknown unit 'mins'"), problems.get(0));
    }

    @Test
    void enumsAreCaseInsensitiveAndListValidValuesOnError() {
        assertEquals(Mode.SURVIVAL, codecs.decode("survival", Mode.class));
        assertEquals(Mode.DARK_OAK, codecs.decode("dark-oak", Mode.class));
        assertEquals(Mode.DARK_OAK, codecs.decode(" Dark Oak ", Mode.class));
        assertEquals("DARK_OAK", codecs.encode(Mode.DARK_OAK, Mode.class));
        assertEquals(List.of("unknown value 'hardcore', valid values: SURVIVAL, DARK_OAK, CREATIVE"),
                problemsOf(() -> codecs.decode("hardcore", Mode.class)));
        assertEquals(List.of("expected one of SURVIVAL, DARK_OAK, CREATIVE, got number 3"),
                problemsOf(() -> codecs.decode(3, Mode.class)));
    }

    // --- collections, optional ---------------------------------------------------------------------------------

    @Test
    void collectionsKeepOrderAndAreUnmodifiable() {
        final List<String> list = codecs.decode(List.of("b", "a"), new TypeToken<List<String>>() { });
        assertEquals(List.of("b", "a"), list);
        assertThrows(UnsupportedOperationException.class, () -> list.add("c"));

        final Set<Mode> set = codecs.decode(List.of("creative", "survival"), new TypeToken<Set<Mode>>() { });
        assertEquals(List.of(Mode.CREATIVE, Mode.SURVIVAL), new ArrayList<>(set));
        assertThrows(UnsupportedOperationException.class, () -> set.add(Mode.DARK_OAK));

        final Map<String, Integer> decoded = codecs.decode(map("z", 1, "a", 2), new TypeToken<Map<String, Integer>>() { });
        assertEquals(List.of("z", "a"), new ArrayList<>(decoded.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> decoded.put("x", 3));
    }

    @Test
    void genericCollectionsRoundTrip() {
        final Map<String, Zone> zones = new LinkedHashMap<>();
        zones.put("spawn", new Zone("world", 0, 64, 0, 8));
        zones.put("nether", new Zone("world_nether", -5, 70, 12, 3));
        assertRoundTrip(List.of(zones, Map.of()), new TypeToken<List<Map<String, Zone>>>() { });
        assertRoundTrip(Map.of("a", List.of(1, 2), "b", List.<Integer>of()), new TypeToken<Map<String, List<Integer>>>() { });
        assertRoundTrip(Set.of(Mode.CREATIVE), new TypeToken<Set<Mode>>() { });
        assertRoundTrip(List.of(Optional.of("x"), Optional.<String>empty()), new TypeToken<List<Optional<String>>>() { });
    }

    @Test
    void collectionErrorsHaveIndexedPaths() {
        assertEquals(List.of("[1]: expected an integer, got string \"x\"", "[2]: must not be null"),
                problemsOf(() -> codecs.decode(java.util.Arrays.asList(1, "x", null), new TypeToken<List<Integer>>() { })));
        assertEquals(List.of("expected a list, got a section"),
                problemsOf(() -> codecs.decode(map(), new TypeToken<List<Integer>>() { })));
        assertEquals(List.of("b: expected an integer, got string \"x\""),
                problemsOf(() -> codecs.decode(map("a", 1, "b", "x"), new TypeToken<Map<String, Integer>>() { })));
    }

    @Test
    void optionalAcceptsNullAndEmpty() {
        final TypeToken<Optional<Integer>> type = new TypeToken<>() { };
        assertEquals(Optional.empty(), codecs.decode(null, type));
        assertEquals(Optional.of(3), codecs.decode(3, type));
        assertEquals(3, codecs.encode(Optional.of(3), type));
        assertEquals(null, codecs.encode(Optional.empty(), type));
        assertRoundTrip(Optional.of(3), type);
        assertRoundTrip(Optional.empty(), type);
        assertEquals(List.of("must not be null"), problemsOf(() -> codecs.decode(null, int.class)));
    }

    // --- records -----------------------------------------------------------------------------------------------

    @Test
    void recordEncodesToKebabCaseKeysInOrder() {
        final Object tree = codecs.encode(AuctionConfig.defaults());
        final Map<?, ?> root = assertInstanceOf(Map.class, tree);
        assertEquals(List.of("min-bid", "bid-time", "anti-snipe", "blacklist", "zones"), new ArrayList<>(root.keySet()));
        assertEquals(100, root.get("min-bid"));
        assertEquals("1m", root.get("bid-time"));
        assertEquals(map("enabled", true, "extend-seconds", 10), root.get("anti-snipe"));
        assertEquals(List.of("BEDROCK"), root.get("blacklist"));
        assertEquals(map("spawn", map("world", "world", "x", 0, "y", 64, "z", 0, "radius", 8)), root.get("zones"));
        assertRoundTrip(AuctionConfig.defaults(), AuctionConfig.class);
    }

    @Test
    void kebabCaseHandlesAcronymsAndDigits() {
        final Fixtures.HttpSettings settings = new Fixtures.HttpSettings("https://x", 3, "n");
        final Map<?, ?> tree = assertInstanceOf(Map.class, codecs.encode(settings));
        assertEquals(List.of("base-url", "max-http-retries", "uuid2-name"), new ArrayList<>(tree.keySet()));
        assertRoundTrip(settings, Fixtures.HttpSettings.class);
    }

    @Test
    void everythingRoundTrips() {
        final Map<String, Zone> zones = Map.of("spawn", new Zone("world", 1, 2, 3, 4));
        final Everything value = new Everything(true, false, 7, 8, 9_000_000_000L, 0.5, 0.25f, "text", Mode.CREATIVE,
                Duration.ofSeconds(90), UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                new NamespacedKey("synthetix", "k"), Key.key("synthetix", "k2"), Set.of(Mode.SURVIVAL),
                Optional.of("here"), Optional.empty(), null, List.of(zones));
        final Map<?, ?> tree = assertInstanceOf(Map.class, codecs.encode(value));
        assertFalse(tree.containsKey("absent"), "empty Optional is omitted");
        assertFalse(tree.containsKey("nothing"), "null @Nullable is omitted");
        assertEquals("here", tree.get("maybe"));
        assertRoundTrip(value, Everything.class);
    }

    @Test
    void jsonKeepsNumberTypesExact() {
        final Everything value = new Everything(false, true, Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE, 5.0,
                1.0E10f, "", Mode.SURVIVAL, Duration.ZERO, new UUID(0, 0), new NamespacedKey("a", "b"), Key.key("a", "b"),
                Set.of(), Optional.empty(), Optional.empty(), "present", List.of());
        assertRoundTrip(value, Everything.class);
    }

    @Test
    void missingKeysAreReported() {
        final Map<String, Object> tree = validConfigTree();
        tree.remove("bid-time");
        tree.put("anti-snipe", map("enabled", true));
        assertEquals(List.of("bid-time: missing", "anti-snipe.extend-seconds: missing"),
                problemsOf(() -> codecs.decode(tree, AuctionConfig.class)));
    }

    @Test
    void nullForNonOptionalIsAnError() {
        final Map<String, Object> tree = validConfigTree();
        tree.put("bid-time", null);
        assertEquals(List.of("bid-time: must not be null"), problemsOf(() -> codecs.decode(tree, AuctionConfig.class)));
    }

    @Test
    void optionalAndNullableComponentsMayBeMissingOrNull() {
        final Map<String, Object> tree = map("flag", true, "boxed-flag", false, "number", 1, "boxed-number", 2,
                "big", 3, "decimal", 1.5, "small", 2, "text", "t", "mode", "creative", "duration", "1s",
                "id", "123e4567-e89b-12d3-a456-426614174000", "namespaced", "a:b", "adventure", "a:b",
                "modes", List.of(), "maybe", null, "nested", List.of());
        final Everything decoded = codecs.decode(tree, Everything.class);
        assertEquals(Optional.empty(), decoded.maybe());
        assertEquals(Optional.empty(), decoded.absent());
        assertEquals(null, decoded.nothing());
    }

    @Test
    void rangeAndNotEmptyErrorsHaveCorrectPaths() {
        final Map<String, Object> tree = validConfigTree();
        tree.put("min-bid", 0);
        tree.put("anti-snipe", map("enabled", true, "extend-seconds", -4));
        tree.put("blacklist", List.of());
        tree.put("zones", map("spawn", validZone(), "far", map("world", "w", "x", 1, "y", 2, "z", 3, "radius", 0)));
        assertEquals(List.of(
                        "min-bid: must be >= 1 (was 0)",
                        "anti-snipe.extend-seconds: must be >= 1 (was -4)",
                        "blacklist: must not be empty",
                        "zones.far.radius: must be >= 1 (was 0)"),
                problemsOf(() -> codecs.decode(tree, AuctionConfig.class)),
                "all four problems are collected, not just the first");
    }

    @Test
    void rangeMessagesForMaxAndBoth() {
        assertEquals(List.of("chance: must be <= 1.5 (was 2.5)", "ratio: must be between 0.5 and 2 (was 3.0)"),
                problemsOf(() -> codecs.decode(map("chance", 2.5, "ratio", 3.0), Fixtures.Doubles.class)));
        assertEquals(new Fixtures.Doubles(1.5, Optional.of(0.5)),
                codecs.decode(map("chance", 1.5, "ratio", 0.5), Fixtures.Doubles.class));
        assertEquals(new Fixtures.Doubles(0, Optional.empty()), codecs.decode(map("chance", 0), Fixtures.Doubles.class));
        assertEquals(List.of("chance: must be <= 1.5 (was NaN)"),
                problemsOf(() -> codecs.decode(map("chance", Double.NaN), Fixtures.Doubles.class)));
    }

    @Test
    void wrongKindsAreReportedAtTheirPath() {
        final Map<String, Object> tree = validConfigTree();
        tree.put("anti-snipe", "yes");
        tree.put("zones", List.of());
        tree.put("blacklist", "BEDROCK");
        assertEquals(List.of(
                        "anti-snipe: expected a section, got string \"yes\"",
                        "blacklist: expected a list, got string \"BEDROCK\"",
                        "zones: expected a section, got a list"),
                problemsOf(() -> codecs.decode(tree, AuctionConfig.class)));
        assertEquals(List.of("expected a section, got string \"x\""),
                problemsOf(() -> codecs.decode("x", AuctionConfig.class)));
    }

    @Test
    void unknownKeysAreWarningsNotErrors() {
        final Map<String, Object> tree = validConfigTree();
        tree.put("min-price", 5);
        tree.put("anti-snipe", map("enabled", true, "extend-seconds", 10, "sneaky", 1));
        final DecodeContext ctx = codecs.newContext();
        final AuctionConfig config = codecs.decode(tree, AuctionConfig.class, ctx);
        assertEquals(AuctionConfig.defaults(), config);
        assertEquals(List.of("anti-snipe.sneaky: unknown key", "min-price: unknown key"),
                ctx.warnings().stream().map(Problem::toString).toList());
    }

    @Test
    void constructorValidationBecomesAProblem() {
        assertEquals(List.of("unlucky number"), problemsOf(() -> codecs.decode(map("value", 13), Fixtures.Checked.class)));
        assertEquals(new Fixtures.Checked(12), codecs.decode(map("value", 12), Fixtures.Checked.class));
    }

    @Test
    void recursiveRecordsWork() {
        final Node tree = new Node("root", List.of(new Node("a", List.of(new Node("a1", List.of()))), new Node("b", List.of())));
        assertRoundTrip(tree, Node.class);
        assertEquals(List.of("children[0].children[0].name: missing"), problemsOf(() -> codecs.decode(
                map("name", "root", "children", List.of(map("name", "a", "children", List.of(map("children", List.of()))))),
                Node.class)));
    }

    @Test
    void genericRecordsResolveTheirTypeArguments() {
        final Page<Zone> page = new Page<>(List.of(new Zone("w", 1, 2, 3, 4)), Optional.of(new Zone("w", 5, 6, 7, 8)));
        assertRoundTrip(page, new TypeToken<Page<Zone>>() { });
        assertRoundTrip(new Page<>(List.of(1, 2), Optional.empty()), new TypeToken<Page<Integer>>() { });
        assertRoundTrip(new Page<>(List.of(List.of("a")), Optional.empty()), new TypeToken<Page<List<String>>>() { });
        assertEquals(List.of("items[0].radius: must be >= 1 (was 0)"), problemsOf(() -> codecs.decode(
                map("items", List.of(map("world", "w", "x", 1, "y", 2, "z", 3, "radius", 0))),
                new TypeToken<Page<Zone>>() { })));
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(map("items", List.of()), Page.class));
    }

    // --- sealed polymorphism -----------------------------------------------------------------------------------

    @Test
    void sealedRecordsUseATypeDiscriminator() {
        final List<Reward> rewards = List.of(new ItemReward("DIAMOND", 50), new CommandReward("eco give %player% 100"),
                new MoneyReward(2.5));
        final Object tree = codecs.encode(rewards, new TypeToken<List<Reward>>() { });
        assertEquals(List.of(
                map("type", "item", "material", "DIAMOND", "chance", 50),
                map("type", "cmd", "command", "eco give %player% 100"),
                map("type", "money", "amount", 2.5)), tree);
        assertEquals("type", ((Map<?, ?>) ((List<?>) tree).get(0)).keySet().iterator().next(), "type comes first");
        assertRoundTrip(rewards, new TypeToken<List<Reward>>() { });
        assertRoundTrip(new Airdrop("vip", rewards), Airdrop.class);
        assertEquals(new ItemReward("DIAMOND", 50), codecs.decode(map("type", "item", "material", "DIAMOND", "chance", 50), Reward.class));
        assertEquals(map("type", "cmd", "command", "x"), codecs.encode(new CommandReward("x"), Reward.class));
    }

    @Test
    void sealedDispatchReportsBadDiscriminators() {
        final Map<String, Object> tree = map("name", "n", "rewards", List.of(
                map("type", "item", "material", "DIAMOND", "chance", 1),
                map("type", "bogus"),
                map("command", "x"),
                map("type", 5),
                map("type", "item", "material", "DIAMOND", "chance", 500)));
        assertEquals(List.of(
                        "rewards[1].type: unknown type 'bogus', valid types: item, cmd, money",
                        "rewards[2].type: missing (one of: item, cmd, money)",
                        "rewards[3].type: expected a string, got number 5",
                        "rewards[4].chance: must be between 1 and 100 (was 500)"),
                problemsOf(() -> codecs.decode(tree, Airdrop.class)));
    }

    @Test
    void discriminatorIsNotReportedAsUnknownKey() {
        final DecodeContext ctx = codecs.newContext();
        codecs.decode(map("type", "money", "amount", 1, "extra", true), Reward.class, ctx);
        assertEquals(List.of("extra: unknown key"), ctx.warnings().stream().map(Problem::toString).toList());
    }

    @Test
    void nestedSealedInterfacesAreFlattened() {
        final List<Fixtures.Shape> shapes = List.of(new Fixtures.Round(1), new Fixtures.SquareShape(2),
                new Fixtures.TriangleShape(3, 4));
        final Object tree = codecs.encode(shapes, new TypeToken<List<Fixtures.Shape>>() { });
        assertEquals(List.of(map("type", "round", "radius", 1.0), map("type", "square", "side", 2.0),
                map("type", "triangle", "base", 3.0, "height", 4.0)), tree);
        assertRoundTrip(shapes, new TypeToken<List<Fixtures.Shape>>() { });
    }

    @Test
    void duplicateTypeNamesFailAtFirstUse() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> codecs.decode(map("type", "same"), Fixtures.Clash.class));
        assertTrue(e.getMessage().contains("both map to type 'same'"), e.getMessage());
    }

    @Test
    void discriminatorClashAndNonRecordSubtypesFailAtFirstUse() {
        final IllegalArgumentException clash = assertThrows(IllegalArgumentException.class,
                () -> codecs.encode(new Fixtures.TaggedRecord("x"), Fixtures.Tagged.class));
        assertTrue(clash.getMessage().contains("discriminator"), clash.getMessage());
        final IllegalArgumentException open = assertThrows(IllegalArgumentException.class,
                () -> codecs.decode(map(), Fixtures.Open.class));
        assertTrue(open.getMessage().contains("must be a record or a sealed interface"), open.getMessage());
    }

    @Test
    void encodingASealedLeafByRuntimeClassKeepsItsDiscriminator() {
        final CommandReward reward = new CommandReward("x");
        assertEquals(map("type", "cmd", "command", "x"), codecs.encode(reward));
        assertEquals(List.of(map("type", "cmd", "command", "x")), codecs.encode(List.of(reward)));
        assertEquals(reward, codecs.fromJson(codecs.toJson(reward), Reward.class));
        assertEquals(new Fixtures.SquareShape(2), codecs.decode(codecs.encode(new Fixtures.SquareShape(2)), Fixtures.Shape.class));
    }

    @Test
    void aSealedLeafAcceptsItsOwnTypeKey() {
        final DecodeContext ctx = codecs.newContext();
        final Object tree = codecs.encode(new ItemReward("DIAMOND", 5));
        assertEquals(new ItemReward("DIAMOND", 5), codecs.decode(tree, ItemReward.class, ctx));
        assertEquals(List.of(), ctx.warnings());
        assertEquals(List.of("type: unknown type 'other', valid types: item, cmd, money"), problemsOf(
                () -> codecs.decode(map("type", "other", "material", "DIAMOND", "chance", 5), Reward.class)));
    }

    @Test
    void aSealedLeafOnAFreshInstanceAcceptsItsOwnTypeKey() {
        final Codecs fresh = Codecs.create();   // the sealed interface is never resolved here
        final DecodeContext ctx = fresh.newContext();
        assertEquals(new ItemReward("DIAMOND", 5),
                fresh.decode(map("type", "item", "material", "DIAMOND", "chance", 5), ItemReward.class, ctx));
        assertEquals(List.of(), ctx.warnings());
        final DecodeContext other = fresh.newContext();
        fresh.decode(map("type", "other", "material", "DIAMOND", "chance", 5), ItemReward.class, other);
        assertEquals(List.of("type: unknown key"), other.warnings().stream().map(Problem::toString).toList());
    }

    @Test
    void typeNamesDoNotDependOnTheDeclaredRoot() {
        final List<Fixtures.Angular> angular = List.of(new Fixtures.SquareShape(2), new Fixtures.TriangleShape(3, 4));
        final Object asAngular = codecs.encode(angular, new TypeToken<List<Fixtures.Angular>>() { });
        final Object asShape = codecs.encode(List.<Fixtures.Shape>copyOf(angular), new TypeToken<List<Fixtures.Shape>>() { });
        assertEquals(asShape, asAngular);
        assertEquals(angular, codecs.decode(asShape, new TypeToken<List<Fixtures.Angular>>() { }));
    }

    @Test
    void genericSelfReferencingRecordsResolve() {
        final Fixtures.Tree<Zone> tree = new Fixtures.Tree<>(new Zone("w", 0, 1, 2, 3), List.of(
                new Fixtures.Tree<>(new Zone("w", 1, 1, 1, 1), List.of(
                        new Fixtures.Tree<>(new Zone("w", 2, 2, 2, 2), List.of()))),
                new Fixtures.Tree<>(new Zone("w", 3, 3, 3, 3), List.of())));
        assertRoundTrip(tree, new TypeToken<Fixtures.Tree<Zone>>() { });
    }

    // --- custom codecs -----------------------------------------------------------------------------------------

    @Test
    void customCodecsOverrideAndNestInRecords() {
        codecs.register(Fixtures.Money.class, Codec.of(
                raw -> new Fixtures.Money(Long.parseLong((String) raw)), money -> Long.toString(money.cents())));
        final Fixtures.Wallet wallet = new Fixtures.Wallet(new Fixtures.Money(150), List.of(new Fixtures.Money(1), new Fixtures.Money(2)));
        assertEquals(map("balance", "150", "history", List.of("1", "2")), codecs.encode(wallet));
        assertRoundTrip(wallet, Fixtures.Wallet.class);
        assertEquals(new Fixtures.Money(7), codecs.decode("7", Fixtures.Money.class));
    }

    @Test
    void customCodecParseErrorsBecomeProblemsAtTheirPath() {
        codecs.register(Fixtures.Money.class, Codec.of(
                raw -> new Fixtures.Money(Long.parseLong((String) raw)), money -> Long.toString(money.cents())));
        assertEquals(List.of("balance: For input string: \"abc\"", "history[1]: unexpected number 5"), problemsOf(
                () -> codecs.decode(map("balance", "abc", "history", List.of("1", 5)), Fixtures.Wallet.class)));
    }

    @Test
    void stringBackedCodecs() {
        codecs.register(Fixtures.Money.class, Codec.string(
                s -> new Fixtures.Money(Long.parseLong(s)), m -> Long.toString(m.cents())));
        assertEquals(new Fixtures.Money(5), codecs.decode(5, Fixtures.Money.class), "numbers are accepted as text");
        assertEquals("5", codecs.encode(new Fixtures.Money(5)));
    }

    @Test
    void registeringAfterUseTakesEffect() {
        final Fixtures.Wallet wallet = new Fixtures.Wallet(new Fixtures.Money(1), List.of());
        assertEquals(map("balance", map("cents", 1L), "history", List.of()), codecs.encode(wallet, Fixtures.Wallet.class));
        codecs.register(Fixtures.Money.class, Codec.of(raw -> new Fixtures.Money((Integer) raw), m -> (int) m.cents()));
        assertEquals(map("balance", 1, "history", List.of()), codecs.encode(wallet, Fixtures.Wallet.class));
    }

    @Test
    void customCodecCanDecodeNestedValuesWithPath() {
        codecs.register(Fixtures.Money.class, new Codec<>() {
            @Override
            public Fixtures.Money decode(final Object raw, final DecodeContext ctx) {
                final Zone zone = ctx.at("zone").decode(((Map<?, ?>) raw).get("zone"), Zone.class);
                return new Fixtures.Money(zone.radius());
            }

            @Override
            public Object encode(final Fixtures.Money value) {
                return map("zone", map("world", "w", "x", 0, "y", 0, "z", 0, "radius", (int) value.cents()));
            }
        });
        assertRoundTrip(new Fixtures.Money(9), Fixtures.Money.class);
        assertEquals(List.of("zone.radius: must be >= 1 (was 0)"), problemsOf(() -> codecs.decode(
                map("zone", map("world", "w", "x", 0, "y", 0, "z", 0, "radius", 0)), Fixtures.Money.class)));
    }

    @Test
    void containersCannotBeRegistered() {
        assertThrows(IllegalArgumentException.class, () -> codecs.register(List.class, Codec.of(r -> List.of(), l -> l)));
    }

    // --- unsupported types, annotation misuse ------------------------------------------------------------------

    @Test
    void unsupportedTypesFailWithAnExplanation() {
        final IllegalArgumentException object = assertThrows(IllegalArgumentException.class,
                () -> codecs.decode("x", Object.class));
        assertTrue(object.getMessage().contains("No codec for java.lang.Object"), object.getMessage());
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(map(), Fixtures.IntKeys.class));
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(map(), Fixtures.RawList.class));
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(List.of(), new TypeToken<List<Object>>() { }));
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(List.of(), String[].class));
    }

    @Test
    void recordsMustBePublic() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> codecs.decode(map("x", 1), Fixtures.Hidden.class));
        assertTrue(e.getMessage().contains("must be public"), e.getMessage());
    }

    @Test
    void annotationMisuseIsRejected() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> codecs.decode(map(), Fixtures.BadRange.class))
                .getMessage().contains("@Range"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> codecs.decode(map(), Fixtures.BadNotEmpty.class))
                .getMessage().contains("@NotEmpty"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> codecs.decode(map(), Fixtures.DuplicateKey.class))
                .getMessage().contains("two components with the key 'same'"));
    }

    @Test
    void typeTokenNeedsATypeArgument() {
        assertThrows(IllegalArgumentException.class, () -> new Plain<String>().token());
    }

    private static final class Plain<T> {
        TypeToken<T> token() {
            return new TypeToken<>() { };
        }
    }

    @Test
    void nullComponentThatIsNotNullableCannotBeEncoded() {
        final AuctionConfig broken = new AuctionConfig(1, Duration.ZERO, new AntiSnipe(true, 1), null, Map.of());
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> codecs.encode(broken));
        assertTrue(e.getMessage().contains("blacklist is null"), e.getMessage());
    }

    @Test
    void unsupportedValuesCannotBeEncoded() {
        assertThrows(IllegalArgumentException.class, () -> codecs.encode(new Object()));
        assertThrows(IllegalArgumentException.class, () -> codecs.encode(Map.of(1, 2)));
        assertThrows(NullPointerException.class, () -> codecs.encode((Object) null));
    }

    // --- dynamic encode ----------------------------------------------------------------------------------------

    @Test
    void encodeByRuntimeClassHandlesContainers() {
        final Object tree = codecs.encode(List.of(Map.of("zone", new Zone("w", 1, 2, 3, 4)), Optional.of(5), Mode.CREATIVE));
        assertEquals(List.of(map("zone", map("world", "w", "x", 1, "y", 2, "z", 3, "radius", 4)), 5, "CREATIVE"), tree);
    }

    @Test
    void encodeByRuntimeClassPicksTheMostSpecificRegisteredCodec() {
        assertEquals("a:b", codecs.encode(new NamespacedKey("a", "b")));
        assertEquals("a:b", codecs.encode(Key.key("a", "b")));
    }

    // --- JSON --------------------------------------------------------------------------------------------------

    @Test
    void jsonOutputIsCompactAndOrdered() {
        assertEquals("{\"min-bid\":100,\"bid-time\":\"1m\",\"anti-snipe\":{\"enabled\":true,\"extend-seconds\":10},"
                + "\"blacklist\":[\"BEDROCK\"],\"zones\":{\"spawn\":{\"world\":\"world\",\"x\":0,\"y\":64,\"z\":0,"
                + "\"radius\":8}}}", codecs.toJson(AuctionConfig.defaults()));
        assertEquals("[1,2.5,\"x\",null,true]", codecs.toJson(java.util.Arrays.asList(1, 2.5, "x", null, true)));
    }

    @Test
    void jsonDecodesWithPathsAndAllProblems() {
        assertEquals(List.of("min-bid: must be >= 1 (was 0)", "zones.spawn.radius: must be >= 1 (was 0)"), problemsOf(
                () -> codecs.fromJson("{\"min-bid\":0,\"bid-time\":\"1m\",\"anti-snipe\":{\"enabled\":true,"
                        + "\"extend-seconds\":10},\"blacklist\":[\"X\"],\"zones\":{\"spawn\":{\"world\":\"w\","
                        + "\"x\":0,\"y\":0,\"z\":0,\"radius\":0}}}", AuctionConfig.class)));
    }

    @Test
    void jsonSyntaxErrorsAreDecodeExceptions() {
        for (final String bad : List.of("", "{", "{\"a\":}", "[1,2", "nope", "{} {}", "1 2")) {
            final DecodeException e = assertThrows(DecodeException.class, () -> codecs.fromJson(bad, Map.class), bad);
            assertTrue(e.getMessage().startsWith("invalid JSON"), e.getMessage());
        }
    }

    @Test
    void jsonRejectsDeepNestingInsteadOfOverflowingTheStack() {
        final String deep = "[".repeat(5_000) + "]".repeat(5_000);
        final DecodeException e = assertThrows(DecodeException.class,
                () -> codecs.fromJson(deep, new TypeToken<List<Object>>() { }));
        assertTrue(e.getMessage().contains("nested deeper"), e.getMessage());
    }

    @Test
    void jsonCannotHoldNaNOrInfinity() {
        assertThrows(IllegalArgumentException.class, () -> codecs.toJson(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> codecs.toJson(List.of(Double.POSITIVE_INFINITY)));
    }

    @Test
    void jsonNumbersDecodeToTheRequestedType() {
        assertEquals(5, codecs.fromJson("5", int.class));
        assertEquals(5.0, codecs.fromJson("5", double.class));
        assertEquals(1500.0, codecs.fromJson("1.5e3", double.class));
        assertEquals(Long.MAX_VALUE, codecs.fromJson("9223372036854775807", long.class));
        assertEquals(List.of("out of range for int (-2147483648 to 2147483647, was 9223372036854775807)"),
                problemsOf(() -> codecs.fromJson("9223372036854775807", int.class)));
    }

    // --- caching, threads --------------------------------------------------------------------------------------

    @Test
    void codecsInstancesAreIndependent() {
        final Codecs other = Codecs.create();
        codecs.register(Fixtures.Money.class, Codec.of(raw -> new Fixtures.Money(1), m -> "custom"));
        assertEquals("custom", codecs.encode(new Fixtures.Money(1)));
        assertEquals(map("cents", 1L), other.encode(new Fixtures.Money(1)));
    }

    @Test
    void resolvingTheSameTypeIsStable() {
        final Object first = codecs.encode(AuctionConfig.defaults());
        final Object second = codecs.encode(AuctionConfig.defaults());
        assertEquals(first, second);
        assertEquals(new Zone("world", 0, 64, 0, 8), codecs.decode(validZone(), Zone.class));
    }

    @Test
    void resolvingTheSameGenericTypeThroughTwoPathsIsStable() {
        final Fixtures.Tree<Zone> tree = new Fixtures.Tree<>(new Zone("w", 0, 1, 2, 3), List.of());
        final Object direct = codecs.encode(tree, new TypeToken<Fixtures.Tree<Zone>>() { });
        final Object viaList = codecs.encode(List.of(tree), new TypeToken<List<Fixtures.Tree<Zone>>>() { });
        assertEquals(List.of(direct), viaList);
        assertEquals(tree, codecs.decode(direct, new TypeToken<Fixtures.Tree<Zone>>() { }));
        assertEquals(List.of(tree), codecs.decode(viaList, new TypeToken<List<Fixtures.Tree<Zone>>>() { }));
    }

    @Test
    void concurrentFirstUseIsSafe() throws Exception {
        final int threads = 8;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final List<Callable<AuctionConfig>> jobs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                jobs.add(() -> codecs.decode(codecs.encode(AuctionConfig.defaults()), AuctionConfig.class));
            }
            for (final Future<AuctionConfig> result : pool.invokeAll(jobs, 30, TimeUnit.SECONDS)) {
                assertEquals(AuctionConfig.defaults(), result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
