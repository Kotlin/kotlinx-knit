import kotlinx.knit.build.mavenCentralArtifacts
import kotlinx.knit.build.allSource

plugins {
    id("org.jetbrains.dokka")
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
}
