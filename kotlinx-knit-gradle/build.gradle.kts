plugins {
    buildsrc.conventions.base
    buildsrc.conventions.`kotlin-jvm`
    `kotlin-dsl`
}

dependencies {
    implementation(projects.kotlinxKnitCore)

    implementation(libs.freemarker)
    implementation(libs.dokka.core)

    testImplementation(projects.kotlinxKnitTest)
    testImplementation(kotlin("test-junit"))
}
