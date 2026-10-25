package com.example.myplugin;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import org.jspecify.annotations.NullMarked;

/** Loads libraries that are not shaded (referenced from paper-plugin.yml). */
@NullMarked
public final class MyPluginLoader implements PluginLoader {

    // @snippet:template-loader
    @Override
    public void classloader(final PluginClasspathBuilder classpath) {
        // Database module: add HikariCP (needs the import com.github.dewarepk.synthetixlib.database.SynthetixLibraries)
        // SynthetixLibraries.database(classpath);
    }
    // @end
}
