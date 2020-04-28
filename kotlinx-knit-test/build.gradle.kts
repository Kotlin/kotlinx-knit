plugins {
    id("org.jetbrains.dokka")
}

tasks.dokka {
    outputFormat = "jekyll"
    outputDirectory = "$buildDir/dokka"
}
