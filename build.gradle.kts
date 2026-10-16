plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.roborazzi) apply false
}

tasks.register("verifyHighspec") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the highspec compile, unit, and lint lane."
    dependsOn(
        "checkDocs",
        "verifyLocalRunner",
        ":app:verifyVariantContracts",
        ":core:heuristics:test",
        ":core:model:test",
        ":core:data:test",
        ":core:database:test",
        ":core:network:test",
        ":app:assembleHighspecDebug",
        ":app:testHighspecDebugUnitTest",
        ":feature:library:testDebugUnitTest",
        ":feature:player:testDebugUnitTest",
        ":app:lintHighspecDebug",
    )
}
tasks.register("verifyScreenshots") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies committed visual baselines against the current UI."
    dependsOn(":app:verifyRoborazziBaselineDebug")
}

val requiredDocs = listOf(
    "README.md",
    "WARP.md",
    "docs/spec/product-spec.md",
    "docs/architecture/overview.md",
    "docs/integration/giantbomb.md",
    "docs/testing/local-ci.md",
    "docs/decisions/ADR-0001-foundation.md",
    "docs/decisions/ADR-0002-auth-persistence.md",
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
        "verifyLocalRunner",
        ":app:verifyVariantContracts",
        ":core:heuristics:test",
        ":core:model:test",
        ":core:data:test",
        ":core:database:test",
        ":core:network:test",
        ":app:testBaselineDebugUnitTest",
        ":feature:library:testDebugUnitTest",
        ":feature:player:testDebugUnitTest",
    )
}

tasks.register("verifyLocal") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the full local validation gate, including lint and managed-device smoke tests unless skipped."
    dependsOn(
        "verifyFast",
        "verifyScreenshots",
        ":app:lintBaselineDebug",
    )

    val skipManagedDevice = providers.gradleProperty("skipManagedDevice")
        .map(String::toBooleanStrictOrNull)
        .orElse(false)

    if (!skipManagedDevice.get()) {
        dependsOn(":app:pixel6Api36BaselineDebugAndroidTest")
    }
}

tasks.register("recordScreenshots") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Records committed screenshot baselines for the current UI."
    dependsOn(":app:recordRoborazziBaselineDebug")
}

tasks.register<Exec>("integrationProbeGiantBomb") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Opt-in manual probe for Giant Bomb Premium feeds using the copied feed URL directly."
    commandLine("pwsh", "-NoLogo", "-File", "scripts/Invoke-GiantBombFeedProbe.ps1")
}

tasks.register<Exec>("integrationProbeGiantBombPlayback") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Opt-in manual playback probe that samples extracted Giant Bomb Premium playback targets using the same direct URL handoff shape as the app."
    commandLine("pwsh", "-NoLogo", "-File", "scripts/Invoke-GiantBombPlaybackProbe.ps1")
}


tasks.register("verifyAllLocal") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Offline baseline + highspec unit, screenshot, lint, identity and debug packaging gate."
    dependsOn("verifyFast", "verifyScreenshots", "verifyHighspec", ":app:lintBaselineDebug", ":app:assembleBaselineDebug",
        ":app:assembleBaselineDebugAndroidTest", ":app:assembleHighspecDebugAndroidTest", ":app:verifyRoborazziHighspecDebug")
}


tasks.register<Exec>("verifyLocalRunner") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Tests local CI failure detection and explicit-device safeguards (Python standard library)."
    commandLine(providers.gradleProperty("looktubePython").orElse("python").get(), "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_local_ci.py")
}
