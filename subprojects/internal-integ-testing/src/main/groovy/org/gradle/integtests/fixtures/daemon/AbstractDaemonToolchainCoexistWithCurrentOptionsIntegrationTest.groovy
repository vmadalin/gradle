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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

abstract class AbstractDaemonToolchainCoexistWithCurrentOptionsIntegrationTest extends AbstractDaemonToolchainIntegrationSpec {

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given disabled auto-detection When using daemon toolchain Then option is ignored resolving with expected toolchain"() {
        def otherJvm = AvailableJavaHomes.differentVersion

        given:
        createDaemonJvmToolchainCriteria(otherJvm)

        expect:
        succeedsSimpleTaskWithDaemonJvm(otherJvm, ["-Porg.gradle.java.installations.auto-detect=false"])
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined org.gradle.java.home gradle property When using daemon toolchain Then option is ignored resolving with expected toolchain"() {
        def currentJvm = Jvm.current()
        def otherJvm = AvailableJavaHomes.differentVersion

        given:
        createDaemonJvmToolchainCriteria(otherJvm)
        file("gradle.properties").writeProperties("org.gradle.java.home": currentJvm.javaHome.canonicalPath)

        expect:
        succeedsSimpleTaskWithDaemonJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain properties When executing any task passing them as arguments Then those are ignored since aren't defined on build properties file"() {
        def currentJvm = Jvm.current()
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        expect:
        succeedsSimpleTaskWithDaemonJvm(currentJvm, ["-Pdaemon.jvm.toolchain.version=$otherJvmMetadata.javaVersion".toString(), "-Pdaemon.jvm.toolchain.vendor=$otherJvmMetadata.vendor.knownVendor".toString()])
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain properties defined on gradle properties When executing any task Then those are ignored since aren't defined on build properties file"() {
        def currentJvm = Jvm.current()
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        file("gradle.properties")
            .writeProperties(
                "daemon.jvm.toolchain.version": otherJvmMetadata.javaVersion,
                "daemon.jvm.toolchain.vendor": otherJvmMetadata.vendor.knownVendor.name()
            )

        expect:
        succeedsSimpleTaskWithDaemonJvm(currentJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined org.gradle.java.home under Build properties When executing any task Then this is ignored since isn't defined on gradle properties file"() {
        def currentJvm = Jvm.current()
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        createDir("gradle")
        file("gradle/gradle-build.properties")
            .writeProperties(
                "org.gradle.java.home": otherJvmMetadata.javaVersion,
            )

        expect:
        succeedsSimpleTaskWithDaemonJvm(currentJvm)
    }
}
