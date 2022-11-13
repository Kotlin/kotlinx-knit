import kotlinx.knit.build.*

plugins {
    buildsrc.conventions.`kotlin-jvm`
    id("org.jetbrains.dokka")
    signing
    `maven-publish`
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
//    implementation(platform(libs.jackson.bom))
//    implementation(libs.jackson.kotlin)
//    implementation(libs.jackson.xml)

    dokkaHtmlPlugin(projects.kotlinxKnitDokka)
    dokkaJekyllPlugin(projects.kotlinxKnitDokka)

    testImplementation(kotlin("test-junit"))
}

publishing {
    publications {
        create<MavenPublication>("kotlinxKnitTest") {
            from(components["java"])
//            mavenCentralArtifacts(project, project.sourceSets.main.allSource)
        }
    }

    mavenCentralMetadata()
    mavenRepositoryPublishing(project)
    publications.withType(MavenPublication::class).all {
        signPublicationIfKeyPresent(this)
    }
}
