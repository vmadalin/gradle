/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.filelock.DefaultLockOptions;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultJdkCacheDirectory implements JdkCacheDirectory {

    private final File jdkDirectory;
    private final FileLockManager lockManager;
    private final JdkCacheDirectoryInstaller jdkInstaller;

    @Inject
    public DefaultJdkCacheDirectory(GradleUserHomeDirProvider homeDirProvider, FileLockManager lockManager, JdkCacheDirectoryInstaller jdkInstaller) {
        this.jdkDirectory = new File(homeDirProvider.getGradleUserHomeDirectory(), "jdks");
        this.lockManager = lockManager;
        this.jdkInstaller = jdkInstaller;
        jdkDirectory.mkdir();
    }

    @Override
    public Set<File> listJavaHomes() {
        final File[] candidates = jdkDirectory.listFiles();
        if (candidates != null) {
            return Arrays.stream(candidates)
                    .flatMap(JdkCacheDirectoryFileMarker::allMarkedLocations)
                    .map(JdkCacheDirectoryFileMarker::getMarkedLocationJavaHome)
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    /**
     * Unpacks and installs the given JDK archive. Returns a file pointing to the java home directory.
     */
    public File provisionFromArchive(JavaToolchainSpec spec, File jdkArchive, URI uri) throws IOException {
        return jdkInstaller.installFromArchive(spec, jdkArchive, jdkDirectory, uri);
    }

    public FileLock acquireWriteLock(File destinationFile, String operationName) {
        return lockManager.lock(destinationFile, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), destinationFile.getName(), operationName);
    }

    public File getDownloadLocation() {
        return jdkDirectory;
    }
}
