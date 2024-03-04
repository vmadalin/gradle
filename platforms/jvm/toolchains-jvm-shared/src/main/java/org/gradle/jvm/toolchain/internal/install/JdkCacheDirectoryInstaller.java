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

package org.gradle.jvm.toolchain.internal.install;

import com.google.common.io.Files;
import org.gradle.api.GradleException;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.jvm.toolchain.internal.JvmInstallationMetadataMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import static org.gradle.jvm.toolchain.internal.install.JdkCacheDirectoryFileMarker.getMarkedLocationJavaHome;
import static org.gradle.jvm.toolchain.internal.install.JdkCacheDirectoryFileMarker.isMarkedLocation;
import static org.gradle.jvm.toolchain.internal.install.JdkCacheDirectoryFileMarker.markAsReady;
import static org.gradle.jvm.toolchain.internal.install.JdkCacheDirectoryFileMarker.markedLocation;

public class JdkCacheDirectoryInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkCacheDirectoryInstaller.class);

    private final JvmMetadataDetector detector;
    private final JdkFileOperations jdkOperations;

    @Inject
    public JdkCacheDirectoryInstaller(JvmMetadataDetector detector, JdkFileOperations jdkOperations) {
        this.detector = detector;
        this.jdkOperations = jdkOperations;
    }

    /**
     * Unpacks and installs the given JDK archive. Returns a file pointing to the java home directory.
     */
    public File installFromArchive(JavaToolchainSpec spec, File jdkArchive, File jdkDirectory, URI uri) throws IOException {
        final File[] unpackFolder = new File[1];
        final File[] installFolder = new File[1];

        try {
            String unpackFolderName = getNameWithoutExtension(jdkArchive);
            final File targetUnpackDirectory = new File(jdkDirectory, unpackFolderName);
            jdkOperations.unpack(jdkArchive, targetUnpackDirectory);
            unpackFolder[0] = targetUnpackDirectory;

            //put the marker file in the unpack folder from where it will get in its proper place
            //when the contents are copied to the installation folder
            File markedLocation = markLocationInsideFolder(unpackFolder[0]);

            //probe unpacked installation for its metadata
            JvmInstallationMetadata metadata = getMetadata(markedLocation);

            validateMetadataMatchesSpec(spec, uri, metadata);

            String installFolderName = getInstallFolderName(metadata);
            installFolder[0] = new File(jdkDirectory, installFolderName);

            //make sure the install folder is empty
            checkInstallFolderForLeftoverContent(installFolder[0], uri, spec, metadata);
            jdkOperations.delete(installFolder[0]);

            //copy content of unpack folder to install folder, including the marker file
            jdkOperations.copy(unpackFolder[0], installFolder[0]);

            LOGGER.info("Installed toolchain from {} into {}", uri, installFolder[0]);
            return getMarkedLocationJavaHome(markedLocation(installFolder[0]));
        } catch (Throwable t) {
            // provisioning failed, clean up leftovers
            if (installFolder[0] != null) {
                jdkOperations.delete(installFolder[0]);
            }
            throw t;
        } finally {
            // clean up temporary unpack folder, regardless if provisioning succeeded or not
            if (unpackFolder[0] != null) {
                jdkOperations.delete(unpackFolder[0]);
            }
        }
    }

    private void checkInstallFolderForLeftoverContent(File installFolder, URI uri, JavaToolchainSpec spec, JvmInstallationMetadata metadata) {
        if (!installFolder.exists()) {
            return; //install folder doesn't even exist
        }

        File[] filesInInstallFolder = installFolder.listFiles();
        if (filesInInstallFolder == null || filesInInstallFolder.length == 0) {
            return; //no files in install folder
        }

        File markerLocation = markedLocation(installFolder);
        if (!isMarkedLocation(markerLocation)) {
            return; //no marker found
        }

        String leftoverMetadata;
        try {
            leftoverMetadata = getMetadata(markerLocation).toString();
        } catch (Exception e) {
            LOGGER.debug("Failed determining metadata of installation leftover", e);
            leftoverMetadata = "Could not be determined due to: " + e.getMessage();
        }
        LOGGER.warn("While provisioning Java toolchain from '{}' to satisfy spec '{}' (with metadata '{}'), " +
                "leftover content (with metadata '{}') was found in the install folder '{}'. " +
                "The existing installation will be replaced by the new download.",
            uri, spec, metadata, leftoverMetadata, installFolder);
    }

    private JvmInstallationMetadata getMetadata(File markedLocation) {
        File javaHome = getMarkedLocationJavaHome(markedLocation);

        JvmInstallationMetadata metadata = detector.getMetadata(InstallationLocation.autoProvisioned(javaHome, "provisioned toolchain"));
        if (!metadata.isValidInstallation()) {
            throw new GradleException("Provisioned toolchain '" + javaHome + "' could not be probed: " + metadata.getErrorMessage(), metadata.getErrorCause());
        }

        return metadata;
    }

    private File markLocationInsideFolder(File unpackedInstallationFolder) {
        File markedLocation = markedLocation(unpackedInstallationFolder);
        markAsReady(markedLocation);
        return markedLocation;
    }

    private static void validateMetadataMatchesSpec(JavaToolchainSpec spec, URI uri, JvmInstallationMetadata metadata) {
        if (!new JvmInstallationMetadataMatcher(spec).test(metadata)) {
            throw new GradleException("Toolchain provisioned from '" + uri + "' doesn't satisfy the specification: " + spec.getDisplayName() + ".");
        }
    }

    private static String getInstallFolderName(JvmInstallationMetadata metadata) {
        String vendor = metadata.getJvmVendor();
        if (vendor == null || vendor.isEmpty()) {
            vendor = metadata.getVendor().getRawVendor();
        }
        String version = metadata.getLanguageVersion().getMajorVersion();
        String architecture = metadata.getArchitecture();
        String os = OperatingSystem.current().getFamilyName();
        return String.format("%s-%s-%s-%s", vendor, version, architecture, os)
            .replaceAll("[^a-zA-Z0-9\\-]", "_")
            .toLowerCase(Locale.ROOT);
    }

    private static String getNameWithoutExtension(File file) {
        //remove all extensions, for example for xxx.tar.gz files only xxx should be left
        String output = file.getName();
        String input;
        do {
            input = output;
            output = Files.getNameWithoutExtension(input);
        } while (!input.equals(output));
        return output;
    }
}
