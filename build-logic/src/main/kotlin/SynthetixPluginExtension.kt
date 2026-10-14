import org.gradle.api.provider.Property

/**
 * Settings for a runnable Paper plugin module (convention `synthetix.paper-plugin`).
 */
abstract class SynthetixPluginExtension {
    /**
     * Package that SynthetixLib gets relocated into inside the shaded jar,
     * e.g. `com.github.dewarepk.myplugin.libs`. MUST NOT start with `com.github.dewarepk.synthetixlib`.
     */
    abstract val libsPackage: Property<String>
}
