import kotlinx.knit.build.*

plugins {
    id("org.jetbrains.dokka")
    signing
    `maven-publish`
}

tasks {
    val dokkaVersion: String by project

    val dokka by creating {
        dependsOn(dokkaJekyll)
    }

    dokkaJekyll {
        outputDirectory.set(file("$buildDir/dokka"))
    }

    dokkaHtml {
        outputDirectory.set(file("$buildDir/dokkaHtml"))
    }
}

dependencies {
    dokkaHtmlPlugin(project(":pathsaver"))
    dokkaJekyllPlugin(project(":pathsaver"))
}

publishing {
    publications {
        create<MavenPublication>("kotlinxKnitTest") {
            from(components["java"])
            mavenCentralArtifacts(project, project.sourceSets.main.allSource)
        }
    }

    mavenCentralMetadata()
    mavenRepositoryPublishing(project)
    publications.withType(MavenPublication::class).all {
        signPublicationIfKeyPresent(this)
    }
}
