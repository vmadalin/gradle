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

package org.gradle.internal.buildconfiguration.resolvers;

import org.gradle.platform.Architecture;
import org.gradle.platform.BuildPlatform;
import org.gradle.platform.OperatingSystem;
import org.gradle.platform.internal.CustomBuildPlatform;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ToolchainSupportedPlatformsMatrix {

    private static final List<Architecture> TOOLCHAIN_SUPPORTED_ARCHITECTURES = Arrays.asList(Architecture.AARCH64, Architecture.X86_64);
    private static final List<OperatingSystem> TOOLCHAIN_SUPPORTED_OPERATING_SYSTEM = Arrays.asList(OperatingSystem.values());

    public static List<BuildPlatform> getToolchainSupportedBuildPlatforms() {
        return TOOLCHAIN_SUPPORTED_ARCHITECTURES.stream()
            .flatMap(architecture -> TOOLCHAIN_SUPPORTED_OPERATING_SYSTEM.stream().map(operatingSystem -> new CustomBuildPlatform(architecture, operatingSystem)))
            .collect(Collectors.toList());
    }
}
