plugins {
    buildsrc.conventions.`kotlin-jvm`
    buildsrc.conventions.`maven-publish`
    id("org.jetbrains.dokka")
    buildsrc.conventions.`dokka-docs-share`
}

val dokka: Task by tasks.creating {
    dependsOn(tasks.dokkaJekyll)
}

tasks.dokkaJekyll {
    outputDirectory.set(file("$buildDir/dokka"))
}

tasks.dokkaHtml {
    outputDirectory.set(file("$buildDir/dokkaHtml"))
}

dependencies {
    dokkaHtmlPlugin(projects.kotlinxKnitDokka)
    dokkaJekyllPlugin(projects.kotlinxKnitDokka)

    testImplementation(kotlin("test-junit5"))
}

publishing {
    publications {
        create<MavenPublication>("kotlinxKnitTest") {
            artifactId = "knit-test"
            from(components["java"])
        }
    }
}

val dokkaHtmlSync by tasks.registering(Sync::class) {
    // Dokka doesn't correct cache tasks, so to prevent unnecessary work manually cache
    // dokkaHtml task with this sync task. Since Dokka is slow, this helps build speed.
    from(tasks.dokkaHtml)
    into(temporaryDir)
}

configurations.dokkaHtmlDocsElements {
    // share Dokka HTML with other subprojects
    outgoing { artifact(dokkaHtmlSync) }
}
