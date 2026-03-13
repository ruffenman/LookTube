plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

val requiredDocs = listOf(
    "README.md",
    "WARP.md",
    "docs/spec/product-spec.md",
    "docs/architecture/overview.md",
    "docs/integration/giantbomb.md",
    "docs/testing/local-ci.md",
    "docs/decisions/ADR-0001-foundation.md",
    "docs/learned-notes/2026-03.md",
)

tasks.register<DocsCheckTask>("checkDocs") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks that the required project documentation files exist and contain core validation references."
    requiredDocPaths.set(requiredDocs)
    readmeRequiredFragments.set(
        listOf(
            ".\\gradlew.bat verifyFast",
            ".\\gradlew.bat verifyLocal",
        ),
    )
    productSpecRequiredFragments.set(
        listOf(
            "Acceptance criteria",
        ),
    )
}

tasks.register("verifyFast") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the fast Ralph loop validation lane."
    dependsOn(
        "checkDocs",
        ":core:model:test",
        ":core:data:test",
        ":core:database:test",
        ":core:network:test",
        ":app:testDebugUnitTest",
    )
}

tasks.register("verifyLocal") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the full local validation gate, including lint and managed-device smoke tests unless skipped."
    dependsOn(
        "verifyFast",
        ":app:lintDebug",
    )

    val skipManagedDevice = providers.gradleProperty("skipManagedDevice")
        .map(String::toBooleanStrictOrNull)
        .orElse(false)

    if (!skipManagedDevice.get()) {
        dependsOn(":app:pixel6Api36DebugAndroidTest")
    }
}

tasks.register("recordScreenshots") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Placeholder task for future screenshot baseline recording."
    doLast {
        logger.lifecycle("No screenshot baselines are wired yet. Add screenshot recording as the first stable visual slice lands.")
    }
}

tasks.register<Exec>("integrationProbeGiantBomb") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Opt-in manual probe for Giant Bomb Premium feed integration using local credentials."
    commandLine("pwsh", "-NoLogo", "-File", "scripts/Invoke-GiantBombFeedProbe.ps1")
}
