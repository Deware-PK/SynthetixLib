package com.github.dewarepk.synthetixlib.database;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.util.Objects;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Libraries that SynthetixLib modules need at runtime but that are not shaded, for your Paper {@code PluginLoader}
 * (set {@code loader:} in {@code paper-plugin.yml}).
 *
 * <pre>{@code
 * public final class AuctionLoader implements PluginLoader {
 *     @Override public void classloader(PluginClasspathBuilder classpath) {
 *         SynthetixLibraries.database(classpath);
 *     }
 * }
 * }</pre>
 */
public final class SynthetixLibraries {

    private SynthetixLibraries() {
    }

    /**
     * Adds HikariCP (the version this library was built with) from Paper's Maven Central mirror. The JDBC drivers are
     * not added: the ones Paper ships (SQLite, MySQL) are used.
     *
     * @param classpath the builder Paper hands to {@code PluginLoader#classloader}
     */
    public static void database(final PluginClasspathBuilder classpath) {
        Objects.requireNonNull(classpath, "classpath");
        final MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        resolver.addDependency(new Dependency(
                new DefaultArtifact("com.zaxxer:HikariCP:" + LibraryVersions.HIKARICP), null));
        classpath.addLibrary(resolver);
    }
}
