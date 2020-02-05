/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit.build

import org.gradle.api.publish.maven.*

fun MavenPublication.mavenCentralMetadata() {
    pom {
        name.set("Knit")
        description.set("Kotlin source code documentation management tool")
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