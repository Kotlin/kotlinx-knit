plugins {
    buildsrc.conventions.base
    buildsrc.conventions.`kotlin-jvm`
//    buildsrc.conventions.`maven-publish`
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
