plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    kotlin("multiplatform").apply(false)
    id("com.android.application").apply(false)
    id("com.android.library").apply(false)
    id("org.jetbrains.compose").apply(false)
    id("com.diffplug.spotless")
}

spotless {
    val ktlintEditorConfig =
        mapOf(
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            "ktlint_standard_filename" to "disabled",
            "ktlint_standard_property-naming" to "disabled",
            "ktlint_standard_max-line-length" to "disabled",
            "ktlint_standard_no-consecutive-comments" to "disabled",
        )

    kotlin {
        target("**/*.kt")
        targetExclude(
            "**/build/**",
            "**/.gradle/**",
            "**/.kotlin/**",
            "**/.worktrees/**",
        )
        ktlint("1.7.1").editorConfigOverride(ktlintEditorConfig)
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(
            "**/build/**",
            "**/.gradle/**",
            "**/.worktrees/**",
        )
        ktlint("1.7.1").editorConfigOverride(ktlintEditorConfig)
    }

    format("misc") {
        target("**/*.md", "**/.gitignore")
        targetExclude(
            "**/build/**",
            "**/.gradle/**",
            "**/.worktrees/**",
            "docs/superpowers/**",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}
