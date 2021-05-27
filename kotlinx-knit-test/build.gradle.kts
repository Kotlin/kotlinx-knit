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
