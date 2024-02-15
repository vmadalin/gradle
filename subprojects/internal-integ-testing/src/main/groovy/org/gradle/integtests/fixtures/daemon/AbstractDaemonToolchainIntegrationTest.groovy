/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.fixtures.daemon

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Assume

import java.util.regex.Pattern

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
abstract class AbstractDaemonToolchainIntegrationTest extends AbstractDaemonToolchainIntegrationSpec {

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain version When execute any task Then daemon jvm was set up with expected configuration"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        createDaemonJvmToolchainCriteria(otherMetadata.languageVersion.majorVersion)

        expect:
        succeedsSimpleTaskWithDaemonJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain version and vendor When execute any task Then daemon jvm was set up with expected configuration"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        createDaemonJvmToolchainCriteria(otherMetadata.languageVersion.majorVersion, otherMetadata.vendor.knownVendor.name())

        expect:
        succeedsSimpleTaskWithDaemonJvm(otherJvm)
    }

    def "Given daemon toolchain criteria that doesn't match installed ones When execute any task Then fails with the expected message"() {
        given:
        createDaemonJvmToolchainCriteria("100000", "amazon")

        expect:
        failsSimpleTaskWithDescription("Cannot find a Java installation on your machine matching the Daemon JVM defined requirements: " +
            "{languageVersion=100000, vendor=AMAZON, implementation=vendor-specific} for ${OperatingSystem.current()}.")
    }

    @Requires(IntegTestPreconditions.Java11HomeAvailable)
    def "Given daemon toolchain criteria that match installed version but not vendor When execute any task Then fails with the expected message"() {
        def supportedVendors = JvmVendor.KnownJvmVendor.values().toList()
        AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_11)
            .collect { jvm -> AvailableJavaHomes.getJvmInstallationMetadata(jvm)}
            .forEach { metadata -> supportedVendors.remove(metadata.vendor.knownVendor)}
        Assume.assumeTrue(supportedVendors.size() > 0)
        def missingInstalledVendor = supportedVendors.first()

        given:
        createDaemonJvmToolchainCriteria("11", missingInstalledVendor.name())

        expect:
        failsSimpleTaskWithDescription("Cannot find a Java installation on your machine matching the Daemon JVM defined requirements: " +
            "{languageVersion=11, vendor=$missingInstalledVendor, implementation=vendor-specific} for ${OperatingSystem.current()}.")
    }

    @Requires(IntegTestPreconditions.Java11HomeAvailable)
    def "Given daemon toolchain criteria that match installed version and vendor but not implementation When execute any task Then fails with the expected message"() {
        def nonIbmJvm = AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_11).find {!it.isIbmJvm() }
        def nonIbmJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(nonIbmJvm)

        given:
        createDaemonJvmToolchainCriteria(nonIbmJvmMetadata.languageVersion.majorVersion, nonIbmJvmMetadata.vendor.knownVendor.name(), "J9")

        expect:
        failsSimpleTaskWithDescription("Cannot find a Java installation on your machine matching the Daemon JVM defined requirements: " +
            "{languageVersion=11, vendor=AMAZON, implementation=J9} for ${OperatingSystem.current()}.")
    }

    @Requires(IntegTestPreconditions.Java11HomeAvailable)
    def "Given daemon toolchain criteria with placeholder implementation that match installed version and version When execute any task Then daemon jvm was set up with expected configuration"() {
        def jvm = AvailableJavaHomes.getJdk11()
        def jvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(jvm)

        given:
        createDaemonJvmToolchainCriteria(jvmMetadata.languageVersion.majorVersion, jvmMetadata.vendor.knownVendor.name(), "vendor_specific")

        expect:
        succeedsSimpleTaskWithDaemonJvm(jvm)
    }

    def "Given defined invalid criteria When execute updateDaemonJvm with different criteria Then criteria get modified using java home"() {
        def currentJvm = Jvm.current()

        given:
        createDaemonJvmToolchainCriteria("-1", "invalidVendor")

        expect:
        succeedsTaskWithDaemonJvm(currentJvm, [], false, "updateDaemonJvm", "--toolchain-version=20", "--toolchain-vendor=AZUL")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined valid criteria matching with local toolchain When execute updateDaemonJvm with different criteria Then criteria get modified using the expected local toolchain"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        createDaemonJvmToolchainCriteria(otherMetadata.languageVersion.majorVersion, otherMetadata.vendor.knownVendor.name())

        expect:
        succeedsTaskWithDaemonJvm(otherJvm, [], true, "updateDaemonJvm", "--toolchain-version=20", "--toolchain-vendor=AZUL")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given non existing toolchain metadata cache When execute any consecutive tasks Then metadata is resolved only for the first build"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        createDaemonJvmToolchainCriteria(otherMetadata.languageVersion.majorVersion, otherMetadata.vendor.knownVendor.name())

        when:
        def results = (0..2).collect {
            taskExecuter.executeTasks(true, ["--info"], "tasks")
        }

        then:
        results.size() == 3
        def metadataAccessMarker = "Received JVM installation metadata from '$otherJvm.javaHome.absolutePath'"
        1 == countMatches(metadataAccessMarker, results[0].plainTextOutput)
        0 == countMatches(metadataAccessMarker, results[1].plainTextOutput)
        0 == countMatches(metadataAccessMarker, results[2].plainTextOutput)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain and task with specific toolchain When execute task Then metadata is resolved only one time storing resolution into cache shared between daemon setup and task toolchain"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        createDaemonJvmToolchainCriteria(otherMetadata.languageVersion.majorVersion, otherMetadata.vendor.knownVendor.name())
        buildFile << """
            apply plugin: 'jvm-toolchains'
            tasks.register('exec', JavaExec) {
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of($otherMetadata.languageVersion.majorVersion)
                    vendor = JvmVendorSpec.matching("${otherMetadata.vendor.knownVendor.name()}")
                })
                mainClass.set("None")
                jvmArgs = ['-version']
            }
        """

        when:
        def result = taskExecuter.executeTasks(true, ["--info", "-Porg.gradle.java.installations.auto-detect=true"], "exec")

        then:
        !(result instanceof OutputScrapingExecutionFailure)
        def metadataAccessMarker = "Received JVM installation metadata from '$otherJvm.javaHome.absolutePath'"
        1 == countMatches(metadataAccessMarker, result.plainTextOutput)
    }

    private int countMatches(String pattern, String text) {
        return Pattern.compile(Pattern.quote(pattern)).matcher(text).count
    }
}
