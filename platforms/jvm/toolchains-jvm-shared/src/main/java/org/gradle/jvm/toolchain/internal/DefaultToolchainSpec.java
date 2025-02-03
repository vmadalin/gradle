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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.jvm.inspection.JavaInstallationCapability;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class DefaultToolchainSpec implements JavaToolchainSpecInternal {

    private final Property<JavaLanguageVersion> version;
    private final Property<JvmVendorSpec> vendor;
    private final Property<JvmImplementation> implementation;
    private final SetProperty<JavaInstallationCapability> capabilities;

    public static class Key implements JavaToolchainSpecInternal.Key {
        private final JavaLanguageVersion languageVersion;
        private final JvmVendorSpec vendor;
        private final JvmImplementation implementation;
        private final Set<JavaInstallationCapability> capabilities;

        public Key(@Nullable JavaLanguageVersion languageVersion, @Nullable JvmVendorSpec vendor, @Nullable JvmImplementation implementation, Set<JavaInstallationCapability> capabilities) {
            this.languageVersion = languageVersion;
            this.vendor = vendor;
            this.implementation = implementation;
            this.capabilities = capabilities;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key that = (Key) o;
            return Objects.equals(languageVersion, that.languageVersion)
                && Objects.equals(vendor, that.vendor)
                && Objects.equals(implementation, that.implementation)
                && Objects.equals(capabilities, that.capabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hash(languageVersion, vendor, implementation, capabilities);
        }

        @Override
        public String toString() {
            return "DefaultKey{" +
                "languageVersion=" + languageVersion +
                ", vendor=" + vendor +
                ", implementation=" + implementation +
                ", capabilities=" + capabilities +
                '}';
        }
    }

    @Inject
    public DefaultToolchainSpec(PropertyFactory propertyFactory) {
        version = propertyFactory.property(JavaLanguageVersion.class);
        vendor = propertyFactory.property(JvmVendorSpec.class);
        implementation = propertyFactory.property(JvmImplementation.class);
        capabilities = propertyFactory.setProperty(JavaInstallationCapability.class);

        getVendor().convention(getConventionVendor());
        getImplementation().convention(getConventionImplementation());
        getCapabilities().convention(getConventionCapabilities());
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
    public SetProperty<JavaInstallationCapability> getCapabilities() {
        return capabilities;
    }

    @Override
    public JavaToolchainSpecInternal.Key toKey() {
        return new Key(getLanguageVersion().getOrNull(), getVendor().getOrNull(), getImplementation().getOrNull(), getCapabilities().getOrElse(new HashSet<>()));
    }

    @Override
    public boolean isConfigured() {
        return getLanguageVersion().isPresent();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isValid() {
        if (getVendor().getOrNull() == JvmVendorSpec.IBM_SEMERU) {
            // https://github.com/gradle/gradle/issues/23155
            // This should make the spec invalid when the enum gets removed
            DeprecationLogger.deprecateBehaviour("Requesting JVM vendor IBM_SEMERU.")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "ibm_semeru_should_not_be_used")
                .nagUser();
        }
        return getLanguageVersion().isPresent() || isSecondaryPropertiesUnchanged();
    }

    private boolean isSecondaryPropertiesUnchanged() {
        return Objects.equals(getConventionVendor(), getVendor().getOrNull()) &&
            Objects.equals(getConventionImplementation(), getImplementation().getOrNull()) &&
            Objects.equals(getConventionCapabilities(), getCapabilities().getOrNull());
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    private static JvmVendorSpec getConventionVendor() {
        return DefaultJvmVendorSpec.any();
    }

    private static JvmImplementation getConventionImplementation() {
        return JvmImplementation.VENDOR_SPECIFIC;
    }

    private static Set<JavaInstallationCapability> getConventionCapabilities() {
        return JavaInstallationCapability.JDK_CAPABILITIES;
    }
}
