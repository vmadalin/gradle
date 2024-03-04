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

package org.gradle.launcher.daemon.configuration;

import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.archive.Unzip;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.cache.FileLockManager;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.resource.ExternalResourceFactory;
import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainHttpRedirectVerifierFactory;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectoryInstaller;
import org.gradle.jvm.toolchain.internal.install.JdkFileOperations;
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader;
import org.gradle.launcher.daemon.jvm.DaemonJavaInstallationRegistryFactory;
import org.gradle.launcher.daemon.jvm.DaemonJavaToolchainProvisioningService;
import org.gradle.launcher.daemon.jvm.DaemonToolchainExternalResourceFactory;
import org.gradle.launcher.daemon.jvm.JavaInstallationRegistryFactory;
import org.gradle.platform.BuildPlatform;
import org.gradle.process.internal.ExecFactory;

public class DaemonConfigurationServices {

    JavaInstallationRegistryFactory createDaemonJavaInstallationRegistryFactory(JdkCacheDirectory jdkCacheDirectory, JvmMetadataDetector jvmMetadataDetector, ExecFactory execFactory, ProgressLoggerFactory progressLoggerFactory, WindowsRegistry windowsRegistry) {
        return new DaemonJavaInstallationRegistryFactory(jdkCacheDirectory, jvmMetadataDetector, execFactory, progressLoggerFactory, windowsRegistry);
    }

    JdkCacheDirectory createJdkCacheDirectory(GradleUserHomeDirProvider gradleUserHomeDirProvider, FileLockManager fileLockManager, JdkCacheDirectoryInstaller jdkCacheDirectoryInstaller) {
        return new JdkCacheDirectory(gradleUserHomeDirProvider, fileLockManager, jdkCacheDirectoryInstaller);
    }

    JdkCacheDirectoryInstaller createJdkCacheDirectoryInstaller(JvmMetadataDetector jvmMetadataDetector, JdkFileOperations jdkFileOperations) {
        return new JdkCacheDirectoryInstaller(jvmMetadataDetector, jdkFileOperations);
    }

    JdkFileOperations createJdkFileOperations(FileSystem fileSystem) {
        return new DaemonJdkFileOperations(new Unzip(fileSystem));
    }

    JavaToolchainQueryService createJavaToolchainQueryService(JavaInstallationRegistryFactory javaInstallationRegistryFactory, JvmMetadataDetector jvmMetadataDetector, JavaToolchainProvisioningService javaToolchainProvisioningService, FileFactory fileFactory, PropertyFactory propertyFactory, BuildPlatform buildPlatform) {
        return new JavaToolchainQueryService(jvmMetadataDetector, fileFactory, javaToolchainProvisioningService, propertyFactory, buildPlatform);
    }

    JavaToolchainProvisioningService createJavaToolchainProvisioningService(SecureFileDownloader secureFileDownloader, JdkCacheDirectory jdkCacheDirectory) {
        return new DaemonJavaToolchainProvisioningService(secureFileDownloader, jdkCacheDirectory);
    }

    JavaToolchainHttpRedirectVerifierFactory createJavaToolchainHttpRedirectVerifierFactory() {
        return new JavaToolchainHttpRedirectVerifierFactory();
    }

    SecureFileDownloader createSecureFileDownloader(FileSystem fileSystem, ListenerManager listenerManager, JavaToolchainHttpRedirectVerifierFactory httpRedirectVerifierFactory) {
        ExternalResourceFactory externalResourceFactory = new DaemonToolchainExternalResourceFactory(fileSystem, listenerManager, httpRedirectVerifierFactory);
        return new SecureFileDownloader(externalResourceFactory);
    }

    DefaultFilePropertyFactory createDefaultFilePropertyFactory(PropertyHost propertyHost, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory) {
        return new DefaultFilePropertyFactory(propertyHost, fileResolver, fileCollectionFactory);
    }
}
