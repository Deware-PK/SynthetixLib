package com.github.dewarepk.synthetixlib.archtest;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.archtest.fixture.SetsAccessible;
import com.github.dewarepk.synthetixlib.archtest.fixture.StaticArray;
import com.github.dewarepk.synthetixlib.archtest.fixture.UsesHikari;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.lang.reflect.AccessibleObject;
import java.util.Collection;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Hard rules for all SynthetixLib library code. Each rule maps to a locked decision (docs/adr)
 * or to a SnapPlug bug that must never come back (AGENTS.md "Never again").
 * Do not weaken a rule to make code pass - change the code, or write an ADR first.
 */
class ArchitectureTest {

    private static final String LIB = "com.github.dewarepk.synthetixlib";
    private static JavaClasses classes;

    /**
     * ADR 0013: Field/Method/Constructor override setAccessible, so match on the call target's
     * owner being any AccessibleObject subtype (also covers trySetAccessible and the static overload).
     */
    static final ArchRule NO_SET_ACCESSIBLE = noClasses().should().callMethodWhere(
                    target(nameMatching("setAccessible|trySetAccessible"))
                            .and(target(owner(assignableTo(AccessibleObject.class)))))
            .because("ADR 0013: setAccessible is forbidden everywhere");

    /**
     * A static array is mutable global state even when the field is final. Synthetic fields are
     * compiler output ({@code $VALUES} of enums, {@code $SwitchMap$} of switches), not written code.
     */
    static final ArchRule NO_STATIC_ARRAYS = noFields().that().areStatic()
            .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should().haveRawType(DescribedPredicate.describe("an array", JavaClass::isArray))
            .because("SnapPlug bug #4/#7: static arrays are mutable global state (arrays are mutable even when final)");

    /**
     * ADR 0014: HikariCP is loaded by the consumer's PluginLoader and only the database module may use it; inside it
     * one class does, so a missing HikariCP shows up as one clear error.
     */
    static final ArchRule HIKARI_ONLY_IN_DATABASE_POOL = noClasses()
            .that().doNotHaveFullyQualifiedName(LIB + ".database.HikariConnectionSource")
            .should().dependOnClassesThat().resideInAPackage("com.zaxxer..")
            .because("ADR 0014: HikariCP is touched by database.HikariConnectionSource only");

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(LIB);
        if (classes.isEmpty()) {
            throw new IllegalStateException("No SynthetixLib classes imported - check archtest dependencies");
        }
    }

    /** The setAccessible rule must really fire on Field/Method calls, trySetAccessible and the static overload. */
    @Test
    void setAccessibleRuleCatchesViolations() {
        final JavaClasses fixture = new ClassFileImporter().importClasses(SetsAccessible.class);
        final AssertionError error = assertThrows(AssertionError.class, () -> NO_SET_ACCESSIBLE.check(fixture));
        final String message = error.getMessage();
        assertTrue(message.contains("Field.setAccessible"), message);
        assertTrue(message.contains("Method.trySetAccessible"), message);
        assertTrue(message.contains("AccessibleObject.setAccessible"), message);
    }

    /** The static-array rule must really fire on a static final array. */
    @Test
    void staticArrayRuleCatchesViolations() {
        final JavaClasses fixture = new ClassFileImporter().importClasses(StaticArray.class);
        final AssertionError error = assertThrows(AssertionError.class, () -> NO_STATIC_ARRAYS.check(fixture));
        assertTrue(error.getMessage().contains("VALUES"), error.getMessage());
    }

    /** The HikariCP rule must really fire on a class that uses it. */
    @Test
    void hikariRuleCatchesViolations() {
        final JavaClasses fixture = new ClassFileImporter().importClasses(UsesHikari.class);
        final AssertionError error = assertThrows(AssertionError.class, () -> HIKARI_ONLY_IN_DATABASE_POOL.check(fixture));
        assertTrue(error.getMessage().contains("HikariConfig"), error.getMessage());
    }

    /** Database.standalone is for tests and tools: library code uses create/open. */
    @Test
    void libraryCodeDoesNotUseStandaloneDatabases() {
        noClasses().should().callMethodWhere(target(nameMatching("standalone"))
                        .and(target(owner(DescribedPredicate.describe("Database",
                                owner -> owner.getName().equals(LIB + ".database.Database"))))))
                .because("Database.standalone is for unit tests and command-line tools; a plugin uses create")
                .check(classes);
    }

    /** ADR 0014: HikariCP only in the database module's pool class. */
    @Test
    void hikariOnlyInTheDatabasePool() {
        HIKARI_ONLY_IN_DATABASE_POOL.check(classes);
    }

    /** ADR 0008: no NMS / CraftBukkit internals. */
    @Test
    void noServerInternals() {
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("net.minecraft..", "org.bukkit.craftbukkit..", "io.papermc.paper.adventure..")
                .because("ADR 0008: Paper API only, no NMS/CraftBukkit")
                .check(classes);
    }

    /**
     * ADR 0008 + ADR 0013 + ADR 0005: reflection only in config.mapping, no setAccessible,
     * no class lookups by name (breaks after relocation).
     */
    @Test
    void noReflection() {
        noClasses().that().resideOutsideOfPackage(LIB + ".config.mapping..")
                .should().dependOnClassesThat().resideInAPackage("java.lang.reflect..")
                .because("ADR 0013: java.lang.reflect is allowed only in config.mapping")
                .check(classes);
        NO_SET_ACCESSIBLE.check(classes);
        noClasses().should().callMethod(Class.class, "forName", String.class)
                .orShould().callMethod(Class.class, "forName", String.class, boolean.class, ClassLoader.class)
                .because("ADR 0005: class names in strings break after relocation")
                .check(classes);
    }

    /** MiniMessage only. */
    @Test
    void noLegacyText() {
        noClasses().should().dependOnClassesThat()
                .haveFullyQualifiedName("org.bukkit.ChatColor")
                .orShould().dependOnClassesThat().resideInAPackage("net.md_5.bungee..")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer")
                .because("MiniMessage only")
                .check(classes);
    }

    /** SnapPlug bug #8: getHolder() snapshots the block state; always getHolder(false). */
    @Test
    void inventoryHolderWithoutSnapshot() {
        noClasses().should().callMethod(Inventory.class, "getHolder")
                .because("SnapPlug bug #8: use getHolder(false)")
                .check(classes);
    }

    /** SnapPlug bug #8: int custom model data is deprecated; use item_model / data components. */
    @Test
    void noLegacyCustomModelData() {
        noClasses().should().callMethod(ItemMeta.class, "setCustomModelData", Integer.class)
                .because("use item_model + data components")
                .check(classes);
    }

    /** ADR 0004/0005 + SnapPlug bugs #4, #7: no global mutable state. */
    @Test
    void noStaticMutableState() {
        fields().that().areStatic()
                .should().beFinal()
                .because("ADR 0004: no static mutable state (every plugin has its own relocated copy)")
                .check(classes);
        NO_STATIC_ARRAYS.check(classes);
        noFields().that().areStatic()
                .should().haveRawType(assignableTo(Map.class).or(assignableTo(Collection.class)))
                .because("SnapPlug bug #4/#7: static maps leak and race; keep state per SynthetixLib instance")
                .check(classes);
        noFields().that().areStatic().and().areDeclaredInClassesThat().areNotEnums()
                .should().haveRawType(resideInAPackage(LIB + "..").or(assignableTo(Plugin.class)))
                .because("ADR 0004: no static singleton / plugin instance")
                .check(classes);
    }

    /** Folia-safe scheduling goes through the core scheduler facade only. */
    @Test
    void schedulerOnlyThroughFacade() {
        noClasses().that().resideOutsideOfPackage(LIB + ".core.scheduler..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.bukkit.scheduler..", "io.papermc.paper.threadedregions..")
                .because("Folia: use the SynthetixLib scheduler facade")
                .check(classes);
    }

    /** The legacy BukkitScheduler is not region-aware: not even the facade may use it. */
    @Test
    void neverLegacyBukkitScheduler() {
        noClasses().should().callMethod(Bukkit.class, "getScheduler")
                .orShould().callMethod(Server.class, "getScheduler")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.scheduler.BukkitScheduler")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.scheduler.BukkitRunnable")
                .because("Folia: BukkitScheduler is not supported, use Paper's region-aware schedulers")
                .check(classes);
    }

    /** Use the plugin logger, never stdout/stderr. */
    @Test
    void noStandardStreams() {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(classes);
    }

    /** Module ⇄ package mapping: synthetixlib-<m> owns com.github.dewarepk.synthetixlib.<m>. */
    @Test
    void everythingInsideAModulePackage() {
        noClasses().should().resideInAPackage(LIB)
                .because("no classes directly in the root package; each module owns " + LIB + ".<module>")
                .check(classes);
    }
}
