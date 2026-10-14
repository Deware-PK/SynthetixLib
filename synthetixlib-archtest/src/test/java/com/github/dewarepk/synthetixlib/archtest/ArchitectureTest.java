package com.github.dewarepk.synthetixlib.archtest;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.GeneralCodingRules;
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

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(LIB);
        if (classes.isEmpty()) {
            throw new IllegalStateException("No SynthetixLib classes imported - check archtest dependencies");
        }
    }

    /** ADR 0008: no NMS / CraftBukkit internals. */
    @Test
    void noServerInternals() {
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("net.minecraft..", "org.bukkit.craftbukkit..", "io.papermc.paper.adventure..")
                .because("ADR 0008: Paper API only, no NMS/CraftBukkit")
                .check(classes);
    }

    /** ADR 0008 + ADR 0005: no reflection, no class lookups by name (breaks after relocation). */
    @Test
    void noReflection() {
        noClasses().should().dependOnClassesThat().resideInAPackage("java.lang.reflect..")
                .because("ADR 0008: no reflection")
                .check(classes);
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
