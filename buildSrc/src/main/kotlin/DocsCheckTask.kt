import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class DocsCheckTask @Inject constructor(
    private val layout: ProjectLayout,
) : DefaultTask() {
    @get:Input
    abstract val requiredDocPaths: ListProperty<String>

    @get:Input
    abstract val readmeRequiredFragments: ListProperty<String>

    @get:Input
    abstract val productSpecRequiredFragments: ListProperty<String>

    @TaskAction
    fun checkDocs() {
        val projectDir = layout.projectDirectory.asFile
        val missing = requiredDocPaths.get().filterNot { relativePath ->
            projectDir.resolve(relativePath).exists()
        }
        check(missing.isEmpty()) {
            "Missing required documentation files:\n${missing.joinToString(separator = "\n")}"
        }

        val readme = projectDir.resolve("README.md").readText()
        val missingReadmeFragments = readmeRequiredFragments.get().filterNot(readme::contains)
        check(missingReadmeFragments.isEmpty()) {
            "README.md is missing required fragments: ${missingReadmeFragments.joinToString()}"
        }

        val spec = projectDir.resolve("docs/spec/product-spec.md").readText()
        val missingSpecFragments = productSpecRequiredFragments.get().filterNot(spec::contains)
        check(missingSpecFragments.isEmpty()) {
            "docs/spec/product-spec.md is missing required fragments: ${missingSpecFragments.joinToString()}"
        }
    }
}
