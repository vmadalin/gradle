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

package org.gradle.jvm.toolchain.internal.install.exceptions;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.platform.BuildPlatform;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Contextual
public class ToolchainProvisioningNotConfiguredException extends GradleException implements ResolutionProvider {

    private final List<String> resolutions;

    public ToolchainProvisioningNotConfiguredException(
        JavaToolchainSpec specification,
        BuildPlatform buildPlatform,
        String cause,
        String... resolutions
    ) {
        super(String.format(
            "Cannot find a Java installation on your machine matching toolchain requirements: %s for %s on %s and %s",
            specification.getDisplayName(),
            buildPlatform.getOperatingSystem(),
            buildPlatform.getArchitecture().toString().toLowerCase(Locale.getDefault()),
            StringUtils.uncapitalize(cause)));
        this.resolutions = Arrays.asList(resolutions);
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }
}
