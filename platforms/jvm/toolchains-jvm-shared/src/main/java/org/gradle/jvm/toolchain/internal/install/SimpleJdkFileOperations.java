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
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

// TODO move to toolchain-jvm
public class SimpleJdkFileOperations implements JdkFileOperations {

    private final FileOperations fileOperations;

    @Inject
    public SimpleJdkFileOperations(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public void delete(File file) {
        fileOperations.delete(file);
    }

    @Override
    public void copy(File from, File into) {
        fileOperations.copy(copySpec -> {
            copySpec.from(from);
            copySpec.into(into);
        });
    }

    @Override
    public void unpack(File archive, File targetDirectory) throws IOException {
        final FileTree fileTree = asFileTree(archive);
        if (!targetDirectory.exists()) {
            fileOperations.copy(spec -> {
                spec.from(fileTree);
                spec.into(targetDirectory);
                spec.setDuplicatesStrategy(DuplicatesStrategy.WARN);
            });
        }
    }

    private FileTree asFileTree(File jdkArchive) {
        final String extension = FilenameUtils.getExtension(jdkArchive.getName());
        if (Objects.equals(extension, "zip")) {
            return fileOperations.zipTree(jdkArchive);
        }
        return fileOperations.tarTree(fileOperations.getResources().gzip(jdkArchive));
    }
}
