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

package org.gradle.internal.buildconfiguration.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.buildconfiguration.resolvers.ToolchainDownloadRepositoriesResolver;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.platform.BuildPlatform;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.util.Map;

/**
 * Generates or updates Daemon JVM criteria.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class UpdateDaemonJvmTask extends DefaultTask {

    public static final String TASK_NAME = "updateDaemonJvm";

    private final UpdateDaemonJvmModifier updateDaemonJvmModifier;

    private final ToolchainDownloadRepositoriesResolver toolchainDownloadRepositoriesResolver;

    @Inject
    public UpdateDaemonJvmTask(
        UpdateDaemonJvmModifier updateDaemonJvmModifier,
        ToolchainDownloadRepositoriesResolver toolchainDownloadRepositoriesResolver
    ) {
        this.updateDaemonJvmModifier = updateDaemonJvmModifier;
        this.toolchainDownloadRepositoriesResolver = toolchainDownloadRepositoriesResolver;
    }

    @TaskAction
    void generate() {
        assertTaskProvidedInput();

        Integer toolchainVersion = getToolchainVersion().get();
        JvmVendor toolchainVendor = getToolchainVendor().isPresent() ? getToolchainVendor().get().asJvmVendor() : null;
        JvmImplementation toolchainImplementation = getToolchainImplementation().getOrNull();

        Map<BuildPlatform, java.util.Optional<URI>> toolchainDownloadUrlByPlatformMap = toolchainDownloadRepositoriesResolver.resolveToolchainDownloadUrlsByPlatform(toolchainVersion, toolchainVendor, toolchainImplementation);
        updateDaemonJvmModifier.updateJvmCriteria(toolchainVersion, toolchainVendor, toolchainImplementation, toolchainDownloadUrlByPlatformMap);
    }

    @OutputFile
    public File getPropertiesFile() {
        return updateDaemonJvmModifier.getPropertiesFile();
    }

    @Input
    @Optional
    @Option(option = "toolchain-version", description = "The version of the toolchain required to set up Daemon JVM")
    public abstract Property<Integer> getToolchainVersion();

    @Input
    @Optional
    @Option(option = "toolchain-vendor", description = "The vendor of the toolchain required to set up Daemon JVM")
    public abstract Property<KnownJvmVendor> getToolchainVendor();

    @Input
    @Optional
    @Option(option = "toolchain-implementation", description = "The virtual machine implementation of the toolchain required to set up Daemon JVM")
    public abstract Property<JvmImplementation> getToolchainImplementation();

    private void assertTaskProvidedInput() {
        int minimumSupportedVersion = Integer.parseInt(JavaVersion.VERSION_1_8.getMajorVersion());
        int maximumSupportedVersion = Integer.parseInt(JavaVersion.VERSION_HIGHER.getMajorVersion());
        int version = getToolchainVersion().get();
        if (version < minimumSupportedVersion || version > maximumSupportedVersion) {
            String exceptionMessage = String.format("Invalid integer value %d provided for the 'toolchain-version' option. The supported values are in the range [%d, %d].",
                version, minimumSupportedVersion, maximumSupportedVersion);
            throw new IllegalArgumentException(exceptionMessage);
        }
    }
}
