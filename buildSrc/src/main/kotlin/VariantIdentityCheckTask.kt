import org.gradle.api.DefaultTask
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class VariantIdentityCheckTask : DefaultTask() {
    @get:Input abstract val identities: MapProperty<String, String>
    @get:Input abstract val expectedIdentities: MapProperty<String, String>

    @TaskAction fun verify() {
        check(identities.get() == expectedIdentities.get()) {
            "Application IDs changed: debug installs must stay isolated from the normal release. " +
                "Expected ${expectedIdentities.get()}, got ${identities.get()}"
        }
        logger.lifecycle("Verified all four debug/release application IDs.")
    }
}
