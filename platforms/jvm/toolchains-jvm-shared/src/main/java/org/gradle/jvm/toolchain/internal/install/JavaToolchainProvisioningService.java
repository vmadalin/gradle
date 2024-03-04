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

import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.LocalBinaryResource;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.ToolchainDownloadFailedException;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;

// TODO Review about MissingToolchainException and getFileName
public interface JavaToolchainProvisioningService {

    File tryInstall(JavaToolchainSpec spec) throws ToolchainDownloadFailedException;

    default boolean isAutoDownloadEnabled() {
        return true;
    }

    default boolean hasConfiguredToolchainRepositories() {
        return true;
    }

    @Contextual
    class MissingToolchainException extends GradleException {

        public MissingToolchainException(JavaToolchainSpec spec, URI uri, @Nullable Throwable cause) {
            super("Unable to download toolchain matching the requirements (" + spec.getDisplayName() + ") from '" + uri + "'.", cause);
        }
    }

    default String getFileName(URI uri, ExternalResource resource) {
        if (resource instanceof LocalBinaryResource) {
            String fileName = ((LocalBinaryResource) resource).getBaseName();
            if (fileName != null) {
                return fileName;
            }
        }
        ExternalResourceMetaData metaData = resource.getMetaData();
        if (metaData == null) {
            throw ResourceExceptions.getMissing(uri);
        }
        String fileName = metaData.getFilename();
        if (fileName == null) {
            throw new GradleException("Can't determine filename for resource located at: " + uri);
        }
        return fileName;
    }
}
