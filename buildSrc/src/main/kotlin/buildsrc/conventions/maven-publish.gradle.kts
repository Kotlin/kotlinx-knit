package kotlinx.knit.build.buildsrc.conventions

import buildsrc.config.KotlinKnitBuildSettings

plugins {
    `maven-publish`
    signing
}

val knitSettings = extensions.getByType<KotlinKnitBuildSettings>()


val javadocJarStub by tasks.registering(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Empty Javadoc Jar (required by Maven Central)"
    archiveClassifier.set("javadoc")
}


val isReleaseVersion = provider { version.toString().matches(KotlinKnitBuildSettings.releaseVersionRegex) }

val sonatypeReleaseUrl: Provider<String> = isReleaseVersion.map { isRelease ->
    if (isRelease) {
        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    } else {
        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
    }
}

signing {
    useGpgCmd()
    if (knitSettings.signingKey.isPresent && knitSettings.signingPassword.isPresent) {
        logger.lifecycle("[maven-publish convention] signing is enabled for ${project.path}")
        useInMemoryPgpKeys(knitSettings.signingKey.get(), knitSettings.signingPassword.get())
    }
}

// Gradle hasn't updated the signing plugin to be compatible with lazy-configuration, so it needs weird workarounds:
afterEvaluate {
    // Register signatures in afterEvaluate, otherwise the signing plugin creates the signing tasks
    // too early, before all the publications are added. Use .all { }, not .configureEach { },
    // otherwise the signing plugin doesn't create the tasks soon enough.

    if (knitSettings.signingKey.isPresent && knitSettings.signingPassword.isPresent) {
        publishing.publications.all publication@{
            logger.lifecycle("[maven-publish convention] configuring signature for publication ${this@publication.name} in ${project.path}")
            // closureOf is a Gradle Kotlin DSL workaround: https://github.com/gradle/gradle/issues/19903
            signing.sign(closureOf<SignOperation> { signing.sign(this@publication) })
        }
    }
}

publishing {
    repositories {
        if (knitSettings.ossrhUsername.isPresent && knitSettings.ossrhPassword.isPresent) {
            maven(sonatypeReleaseUrl) {
                name = "SonatypeRelease"
                credentials {
                    username = knitSettings.ossrhUsername.get()
                    password = knitSettings.ossrhPassword.get()
                }
            }
        }

        // Publish to a project-local Maven directory, for verification. To test, run:
        // ./gradlew publishAllPublicationsToMavenProjectLocalRepository
        // and check $rootDir/build/maven-project-local
        maven(rootProject.layout.buildDirectory.dir("maven-project-local")) {
            name = "MavenProjectLocal"
        }
    }

    publications.withType<MavenPublication>().configureEach {

        artifact(javadocJarStub)

        pom {
            description.convention("Kotlin source code documentation management tool")
            url.set("https://github.com/Kotlin/kotlinx-knit")
            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("JetBrains")
                    name.set("JetBrains Team")
                    organization.set("JetBrains")
                    organizationUrl.set("https://www.jetbrains.com")
                }
            }
            scm {
                url.set("https://github.com/Kotlin/kotlinx-knit")
            }
        }
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    // Gradle warns about some signing tasks using publishing task outputs without explicit dependencies.
    // Here's a quick fix.
    dependsOn(tasks.withType<Sign>())
    mustRunAfter(tasks.withType<Sign>())

    // use a val for the GAV to avoid Gradle Configuration Cache issues
    val publicationGAV = publication?.run { "$group:$artifactId:$version" }

    doLast {
        if (publicationGAV != null) {
            logger.lifecycle("[task: ${path}] $publicationGAV")
        }
    }
}
