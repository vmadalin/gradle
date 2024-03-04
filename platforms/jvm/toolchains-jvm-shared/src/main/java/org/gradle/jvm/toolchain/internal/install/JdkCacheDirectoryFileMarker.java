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

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

public class JdkCacheDirectoryFileMarker {

    private static final String MAC_OS_JAVA_HOME_FOLDER = "Contents/Home";
    private static final String MARKER_FILE = "provisioned.ok";

    public static Stream<File> allMarkedLocations(File candidate) {
        if (isMarkedLocation(candidate)) {
            return Stream.of(candidate);
        }

        File[] subFolders = candidate.listFiles();
        if (subFolders == null) {
            return Stream.empty();
        }

        return Arrays.stream(subFolders).filter(JdkCacheDirectoryFileMarker::isMarkedLocation);
    }

    public static boolean isMarkedLocation(File candidate) {
        return candidate.isDirectory() && new File(candidate, MARKER_FILE).exists();
    }

    public static File markedLocation(File unpackFolder) {
        File[] content = unpackFolder.listFiles();
        if (content == null) {
            //can't happen, the installation location is a directory, we have created it
            throw new RuntimeException("Programming error");
        }

        //mark the first directory since there should be only one
        for (File file : content) {
            if (file.isDirectory()) {
                return file;
            }
        }

        //there were no sub-directories in the installation location
        return unpackFolder;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static File markAsReady(File destination) {
        try {
            new File(destination, MARKER_FILE).createNewFile();
            return destination;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create .ok file", e);
        }
    }

    public static File getMarkedLocationJavaHome(File markedLocation) {
        if (OperatingSystem.current().isMacOsX()) {
            if (new File(markedLocation, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                return new File(markedLocation, MAC_OS_JAVA_HOME_FOLDER);
            }

            File[] subfolders = markedLocation.listFiles(File::isDirectory);
            if (subfolders != null) {
                for(File subfolder : subfolders) {
                    if (new File(subfolder, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                        return new File(subfolder, MAC_OS_JAVA_HOME_FOLDER);
                    }
                }
            }
        }

        return markedLocation;
    }
}
