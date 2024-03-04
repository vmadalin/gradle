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

import org.gradle.api.internal.file.archive.Unzip;
import org.gradle.jvm.toolchain.internal.install.JdkFileOperations;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.io.IOException;

public class DaemonJdkFileOperations implements JdkFileOperations {

    private final Unzip unzip;

    public DaemonJdkFileOperations(Unzip unzip) {
        this.unzip = unzip;
    }

    @Override
    public void delete(File file) {
        GFileUtils.deleteDirectory(file);
    }

    @Override
    public void copy(File from, File into) {
        GFileUtils.copyDirectory(from, into);
    }

    @Override
    public void unpack(File archive, File targetDirectory) throws IOException {
        // TODO rename
        unzip.unzip(archive, targetDirectory);
    }
}
