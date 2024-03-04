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

import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainRequest;
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainRequest;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec;
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal;
import org.gradle.jvm.toolchain.internal.RealizedJavaToolchainRepository;
import org.gradle.platform.Architecture;
import org.gradle.platform.BuildPlatform;
import org.gradle.platform.OperatingSystem;
import org.gradle.platform.internal.DefaultBuildPlatform;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ToolchainDownloadRepositoriesResolver {

    private final static List<Architecture> TOOLCHAIN_SUPPORTED_ARCHITECTURES = Arrays.asList(Architecture.AARCH64, Architecture.X86_64);
    private final static List<OperatingSystem> TOOLCHAIN_SUPPORTED_OPERATING_SYSTEM = Arrays.asList(OperatingSystem.values());

    private final JavaToolchainResolverRegistryInternal toolchainResolverRegistry;
    private final ObjectFactory objectFactory;

    public ToolchainDownloadRepositoriesResolver(JavaToolchainResolverRegistry toolchainResolverRegistry, ObjectFactory objectFactory) {
        this.toolchainResolverRegistry = (JavaToolchainResolverRegistryInternal) toolchainResolverRegistry;
        this.objectFactory = objectFactory;
    }

    public Map<BuildPlatform, Optional<URI>> resolveToolchainDownloadUrlsByPlatform(
        Integer toolchainVersion,
        @Nullable JvmVendor toolchainVendor,
        @Nullable JvmImplementation toolchainImplementation
    ) throws UnconfiguredToolchainDownloadRepositories {
        List<? extends RealizedJavaToolchainRepository> toolchainRepositories = toolchainResolverRegistry.requestedRepositories();
        if (toolchainRepositories.isEmpty()) {
            throw new UnconfiguredToolchainDownloadRepositories("Toolchain download repositories have not been configured.",
                "Learn more about toolchain repositories at " + Documentation.userManual("toolchains", "sub:download_repositories").getUrl() + ".");
        }

        JavaToolchainSpec toolchainSpec = createToolchainSpec(toolchainVersion, toolchainVendor, toolchainImplementation);
        Map<BuildPlatform, Optional<URI>> toolchainDownloadUrlByPlatformMap = new HashMap<>();

        TOOLCHAIN_SUPPORTED_ARCHITECTURES.stream()
            .flatMap(architecture -> TOOLCHAIN_SUPPORTED_OPERATING_SYSTEM.stream().map(operatingSystem ->
                new DefaultJavaToolchainRequest(toolchainSpec, new DefaultBuildPlatform(architecture, operatingSystem))
            ))
            .collect(Collectors.toList())
            .forEach(javaToolchainRequest -> {
                Optional<URI> downloadUrl = resolveToolchainDownloadUrlRequest(toolchainRepositories, javaToolchainRequest);
                toolchainDownloadUrlByPlatformMap.put(javaToolchainRequest.getBuildPlatform(), downloadUrl);
            });

        return toolchainDownloadUrlByPlatformMap;
    }

    private Optional<URI> resolveToolchainDownloadUrlRequest(List<? extends RealizedJavaToolchainRepository> toolchainRepositories, JavaToolchainRequest javaToolchainRequest) {
        for (RealizedJavaToolchainRepository repository : toolchainRepositories) {
            Optional<JavaToolchainDownload> javaToolchainDownload = repository.getResolver().resolve(javaToolchainRequest);
            if (javaToolchainDownload.isPresent()) {
                return Optional.of(javaToolchainDownload.get().getUri());
            }
        }
        return Optional.empty();
    }

    private JavaToolchainSpec createToolchainSpec(
        Integer toolchainVersion,
        @Nullable JvmVendor toolchainVendor,
        @Nullable JvmImplementation toolchainImplementation
    ) {
        JavaToolchainSpec toolchainSpec = new DefaultToolchainSpec(objectFactory);
        toolchainSpec.getLanguageVersion().set(JavaLanguageVersion.of(toolchainVersion));
        if (toolchainVendor != null) {
            toolchainSpec.getVendor().set(DefaultJvmVendorSpec.of(toolchainVendor.getKnownVendor()));
        }
        toolchainSpec.getImplementation().set(toolchainImplementation);

        return toolchainSpec;
    }
}
