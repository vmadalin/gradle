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

import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.provider.Property;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.gradle.jvm.toolchain.internal.JavaToolchainSpecInternal;

import java.util.Objects;

// TODO this class was removed in favour of DefaultToolchainSpec keeping it for now but making it to implement JavaToolchainSpecInternal
public class DaemonJvmToolchainSpec implements JavaToolchainSpecInternal, JavaToolchainSpec {

    private final Property<JavaLanguageVersion> version;
    private final Property<JvmVendorSpec> vendor;
    private final Property<JvmImplementation> implementation;

    public DaemonJvmToolchainSpec(PropertyFactory propertyFactory) {
        this.version = propertyFactory.property(JavaLanguageVersion.class);
        this.vendor = propertyFactory.property(JvmVendorSpec.class).convention(DefaultJvmVendorSpec.any());
        this.implementation = propertyFactory.property(JvmImplementation.class).convention(JvmImplementation.VENDOR_SPECIFIC);
    }

    @Override
    public Property<JavaLanguageVersion> getLanguageVersion() {
        return version;
    }

    @Override
    public Property<JvmVendorSpec> getVendor() {
        return vendor;
    }

    @Override
    public Property<JvmImplementation> getImplementation() {
        return implementation;
    }

    @Override
    public String getDisplayName() {
        return "test";
    }

    @Override
    public Key toKey() {
        return new JavaToolchainSpecInternal.Key() {
            @Override
            public String toString() {
                return "Test";
            }
        };
    }

    @Override
    public boolean isConfigured() {
        return version.isPresent();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isValid() {
        if (vendor.getOrNull() == JvmVendorSpec.IBM_SEMERU) {
            // https://github.com/gradle/gradle/issues/23155
            // This should make the spec invalid when the enum gets removed
            DeprecationLogger.deprecateBehaviour("Requesting JVM vendor IBM_SEMERU.")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "ibm_semeru_should_not_be_used")
                .nagUser();
        }
        return version.isPresent() || isSecondaryPropertiesUnchanged();
    }

    private boolean isSecondaryPropertiesUnchanged() {
        return Objects.equals(DefaultJvmVendorSpec.any(), vendor.getOrNull()) &&
            Objects.equals(JvmImplementation.VENDOR_SPECIFIC, implementation.getOrNull());
    }

    @Override
    public void finalizeProperties() {
        getLanguageVersion().finalizeValue();
        getVendor().finalizeValue();
        getImplementation().finalizeValue();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
