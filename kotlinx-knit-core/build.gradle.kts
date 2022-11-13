plugins {
    buildsrc.conventions.`kotlin-jvm`
}

dependencies {
    implementation(projects.kotlinxKnitTest)
    implementation(projects.kotlinxKnitDokka)

    implementation(libs.slf4j.api)

    implementation(libs.freemarker)
    implementation(libs.dokka.core)

    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.xml)

    testImplementation(kotlin("test-junit5"))
}
