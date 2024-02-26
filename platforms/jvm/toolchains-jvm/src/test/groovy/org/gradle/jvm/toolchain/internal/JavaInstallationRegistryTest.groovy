/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.logging.Logger
import org.gradle.cache.internal.DefaultCacheFactory
import org.gradle.cache.internal.DefaultFileLockManagerTestHelper
import org.gradle.cache.internal.DefaultUnscopedCacheBuilderFactory
import org.gradle.cache.internal.scopes.DefaultGlobalScopedCacheBuilderFactory
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.initialization.layout.GlobalCacheDir
import org.gradle.internal.SystemProperties
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.jvm.inspection.CachingJvmMetadataDetector
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.internal.progress.RecordingProgressLoggerFactory
import spock.lang.Specification

class JavaInstallationRegistryTest extends Specification {

    def tempFolder = createTempDir()

    def "registry keeps track of initial installations"() {
        when:
        createExecutable(tempFolder)

        def registry = newRegistry(tempFolder)

        then:
        registry.listInstallations()*.location == [tempFolder]
        registry.listInstallations()*.source == ["testSource"]
    }

    def "registry filters non-unique locations"() {
        when:
        createExecutable(tempFolder)

        def registry = newRegistry(tempFolder, tempFolder)

        then:
        registry.listInstallations()*.location == [tempFolder]
    }

    def "duplicates are detected using canonical form"() {
        given:
        createExecutable(tempFolder)

        def registry = newRegistry(tempFolder, new File(tempFolder, "/."))

        when:
        def installations = registry.listInstallations()

        then:
        installations*.location == [tempFolder]
    }

    def "can be initialized with suppliers"() {
        given:
        createExecutable(tempFolder)
        def tmpDir2 = createTempDir()
        createExecutable(tmpDir2)
        def tmpDir3 = createTempDir()
        createExecutable(tmpDir3)

        when:
        def registry = newRegistry(tempFolder, tmpDir2, tmpDir3)

        then:
        registry.listInstallations()*.location.containsAll(tempFolder, tmpDir2, tmpDir2)
    }

    def "list of installations is cached"() {
        given:
        createExecutable(tempFolder)

        def registry = newRegistry(tempFolder)

        when:
        def installations = registry.listInstallations()
        def installations2 = registry.listInstallations()

        then:
        installations.is(installations2)
    }

    def "normalize installations to account for macOS folder layout"() {
        given:
        def expectedHome = new File(tempFolder, "Contents/Home")
        createExecutable(expectedHome, OperatingSystem.MAC_OS)

        def registry = new JavaInstallationRegistry([forDirectory(tempFolder)], metadataDetector(), new TestBuildOperationExecutor(), OperatingSystem.MAC_OS, new NoOpProgressLoggerFactory())

        when:
        def installations = registry.listInstallations()

        then:
        installations*.location.contains(expectedHome)
    }

    def "normalize installations to account for standalone jre"() {
        given:
        def expectedHome = new File(tempFolder, "jre")
        createExecutable(expectedHome)

        def registry = new JavaInstallationRegistry([forDirectory(tempFolder)], metadataDetector(), new TestBuildOperationExecutor(), OperatingSystem.current(), new NoOpProgressLoggerFactory())

        when:
        def installations = registry.listInstallations()

        then:
        installations*.location.contains(expectedHome)
    }

    def "skip path normalization on non-osx systems"() {
        given:
        def rootWithMacOsLayout = createTempDir()
        createExecutable(rootWithMacOsLayout, OperatingSystem.LINUX)
        def expectedHome = new File(rootWithMacOsLayout, "Contents/Home")
        assert expectedHome.mkdirs()

        def registry = new JavaInstallationRegistry([forDirectory(rootWithMacOsLayout)], metadataDetector(), new TestBuildOperationExecutor(), OperatingSystem.LINUX, new NoOpProgressLoggerFactory())

        when:
        def installations = registry.listInstallations()

        then:
        installations*.location.contains(rootWithMacOsLayout)
    }

    def "detecting installations is tracked as build operation"() {
        def executor = new TestBuildOperationExecutor()
        given:
        def registry = new JavaInstallationRegistry(Collections.emptyList(), metadataDetector(), executor, OperatingSystem.current(), new NoOpProgressLoggerFactory())

        when:
        registry.listInstallations()

        then:
        executor.log.getDescriptors().find { it.displayName == "Toolchain detection" }
    }

    def "warns and filters invalid installations, exists: #exists, directory: #directory"() {
        given:
        def file = Mock(File)
        file.exists() >> exists
        file.isDirectory() >> directory
        file.absolutePath >> path
        def logger = Mock(Logger)
        def registry = JavaInstallationRegistry.withLogger([forDirectory(file)], metadataDetector(), logger, new TestBuildOperationExecutor(), new NoOpProgressLoggerFactory())

        when:
        def installations = registry.listInstallations()

        then:
        installations.isEmpty()
        1 * logger.warn(logOutput, "'" + path + "' (testSource)")

        where:
        path        | exists | directory | valid | logOutput
        '/unknown'  | false  | null      | false | 'Directory {} used for java installations does not exist'
        '/foo/file' | true   | false     | false | 'Path for java installation {} points to a file, not a directory'
    }

    def "warns and filters installations without java executable"() {
        given:
        def logger = Mock(Logger)
        def tempFolder = createTempDir()
        def registry = JavaInstallationRegistry.withLogger([forDirectory(tempFolder)], metadataDetector(), logger, new TestBuildOperationExecutor(), new NoOpProgressLoggerFactory())
        def logOutput = "Path for java installation {} does not contain a java executable"

        when:
        def installations = registry.listInstallations()

        then:
        installations.isEmpty()
        1 * logger.warn(logOutput, "'" + tempFolder + "' (testSource)")
    }

    def "can detect enclosed jre installations"() {
        given:
        createExecutable(tempFolder)
        def jreHome = new File(tempFolder, "jre")
        createExecutable(jreHome)

        def registry = newRegistry(jreHome)

        when:
        def installations = registry.listInstallations()

        then:
        installations*.location.contains(tempFolder)
    }

    def "displays progress"() {
        given:
        createExecutable(tempFolder)
        def loggerFactory = new RecordingProgressLoggerFactory()

        def registry = new JavaInstallationRegistry(
            [forDirectory(tempFolder)],
            metadataDetector(),
            new TestBuildOperationExecutor(),
            OperatingSystem.current(),
            loggerFactory
        )

        when:
        registry.toolchains()

        then:
        loggerFactory.recordedMessages.find { it.contains("Extracting toolchain metadata from '$tempFolder'") }
    }

    InstallationSupplier forDirectory(File directory) {
        return new InstallationSupplier() {
            @Override
            String getSourceName() {
                "testSource"
            }

            @Override
            Set<InstallationLocation> get() {
                return Collections.singleton(new InstallationLocation(directory, getSourceName()))
            }
        }
    }

    File createTempDir() {
        def file = File.createTempDir()
        file.deleteOnExit()
        file.canonicalFile
    }

    void createExecutable(File javaHome, os = OperatingSystem.current()) {
        def executableName = os.getExecutableName("java")
        def executable = new File(javaHome, "bin/$executableName")
        assert executable.parentFile.mkdirs()
        executable.createNewFile()
    }

    private JvmMetadataDetector metadataDetector() {
        def execHandleFactory = TestFiles.execHandleFactory();
        def temporaryFileProvider = TestFiles.tmpDirTemporaryFileProvider(new File(SystemProperties.getInstance().getJavaIoTmpDir()));
        def defaultJvmMetadataDetector = new DefaultJvmMetadataDetector(execHandleFactory, temporaryFileProvider)

        def globalCacheDir = new GlobalCacheDir(createHomeDirProvider())
        def cacheFactory = new DefaultCacheFactory(DefaultFileLockManagerTestHelper.createDefaultFileLockManager(), new DefaultExecutorFactory(), new NoOpProgressLoggerFactory())
        def cacheBuilderFactory = new DefaultUnscopedCacheBuilderFactory(cacheFactory)
        def globalScopedCacheBuilderFactory = new DefaultGlobalScopedCacheBuilderFactory(globalCacheDir.dir, cacheBuilderFactory)
        new CachingJvmMetadataDetector(defaultJvmMetadataDetector, globalScopedCacheBuilderFactory)
    }

    private GradleUserHomeDirProvider createHomeDirProvider() {
        return new GradleUserHomeDirProvider() {
            @Override
            File getGradleUserHomeDirectory() {
                return tempFolder
            }
        }
    }

    private JavaInstallationRegistry newRegistry(File... location) {
        new JavaInstallationRegistry(
            location.collect { forDirectory(it) },
            metadataDetector(),
            new TestBuildOperationExecutor(),
            OperatingSystem.current(),
            new NoOpProgressLoggerFactory()
        )
    }
}
