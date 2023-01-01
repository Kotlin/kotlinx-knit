plugins {
    buildsrc.conventions.base
    buildsrc.conventions.`kotlin-jvm`
    `kotlin-dsl`
    com.gradle.`plugin-publish`
}

dependencies {
    implementation(projects.kotlinxKnitCore)

    implementation(libs.freemarker)
    implementation(libs.dokka.core)

    testImplementation(projects.kotlinxKnitTest)
    testImplementation(kotlin("test-junit5"))
}

gradlePlugin {
    plugins {
        create("kotlinx-knit") {
            // This is a fully-qualified plugin id, short id of 'kotlinx-knit' is added manually in resources
            id = "org.jetbrains.kotlinx.knit"
            implementationClass = "kotlinx.knit.gradle.KnitPlugin"
            displayName = "Knit documentation plugin"
            description =
                "Produces Kotlin source example files and tests from Markdown documents with embedded snippets of Kotlin code"
        }
    }
}

pluginBundle {
    website = "https://github.com/Kotlin/kotlinx-knit"
    vcsUrl = "https://github.com/Kotlin/kotlinx-knit"
    tags = listOf("kotlin", "documentation", "markdown")
}

publishing {
    repositories {
        // see buildsrc.conventions.`maven-publish` plugin
        // (copied here because the Gradle publish plugin isn't compatible with the convention plugin.)
        maven(rootProject.layout.buildDirectory.dir("maven-project-local")) {
            name = "MavenProjectLocal"
        }
    }
}
