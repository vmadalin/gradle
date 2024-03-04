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

import org.gradle.internal.buildconfiguration.BuildPropertiesDefaults;
import org.gradle.internal.buildconfiguration.BuildPropertiesModifier;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.platform.BuildPlatform;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class UpdateDaemonJvmModifier extends BuildPropertiesModifier {

    public UpdateDaemonJvmModifier(File projectDir) {
        super(projectDir);
    }

    public void updateJvmCriteria(
        Integer toolchainVersion,
        @Nullable JvmVendor toolchainVendor,
        @Nullable JvmImplementation toolchainImplementation,
        Map<BuildPlatform, Optional<URI>> toolchainDownloadUrlByPlatformMap
    ) {
        updateProperties(buildProperties -> {
            buildProperties.put(BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY, toolchainVersion.toString());
            if (toolchainVendor != null) {
                buildProperties.put(BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY, toolchainVendor.getKnownVendor().name());
            } else {
                buildProperties.remove(BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY);
            }
            if (toolchainImplementation != null) {
                buildProperties.put(BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY, toolchainImplementation.name());
            } else {
                buildProperties.remove(BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY);
            }

            toolchainDownloadUrlByPlatformMap.forEach((buildPlatform, url) -> {
                String toolchainUrlProperty = getToolchainUrlPropertyForPlatform(buildPlatform);
                if (url.isPresent()) {
                    buildProperties.put(toolchainUrlProperty, url.get().toString());
                } else {
                    buildProperties.remove(toolchainUrlProperty);
                }
            });
        });
    }

    private String getToolchainUrlPropertyForPlatform(BuildPlatform buildPlatform) {
        String architecture = buildPlatform.getArchitecture().name().toLowerCase(Locale.ROOT);
        String operatingSystem = buildPlatform.getOperatingSystem().name().replace("_", "").toLowerCase(Locale.ROOT);
        return String.format(BuildPropertiesDefaults.TOOLCHAIN_URL_PROPERTY_FORMAT, operatingSystem, architecture);
    }
}
