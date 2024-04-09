plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Build configuration properties modifiers and helpers."

dependencies {
    api(libs.inject)
    api(libs.jsr305)

    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":jvm-services"))
    api(project(":toolchains-jvm-shared"))

    implementation(project(":base-annotations"))
    implementation(project(":platform-jvm"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":toolchains-jvm-shared")))

    testFixturesImplementation(project(":core-api"))

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.  Toolchain usage requires JVM distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
