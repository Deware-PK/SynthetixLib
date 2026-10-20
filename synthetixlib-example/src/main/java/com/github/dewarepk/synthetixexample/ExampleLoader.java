package com.github.dewarepk.synthetixexample;

import com.github.dewarepk.synthetixlib.database.SynthetixLibraries;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import org.jspecify.annotations.NullMarked;

/** Loads HikariCP for the example plugin (referenced from paper-plugin.yml). */
@NullMarked
public final class ExampleLoader implements PluginLoader {

    @Override
    public void classloader(final PluginClasspathBuilder classpath) {
        SynthetixLibraries.database(classpath);
    }
}
