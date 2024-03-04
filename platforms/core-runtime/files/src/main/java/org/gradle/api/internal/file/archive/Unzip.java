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

package org.gradle.api.internal.file.archive;

import jdk.internal.agent.FileSystem;
import org.gradle.internal.file.Chmod;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.gradle.internal.file.PathTraversalChecker.safePathName;

//TODO rename and potentially find a different module/dir
public class Unzip {

    private final Chmod chmod;

    public Unzip(Chmod chmod) {
        this.chmod = chmod;
    }

    public void unzip(File zip, File dest) throws IOException {
        ZipFile zipFile = new ZipFile(zip);
        try {
            Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                File destFile = new File(dest, safePathName(entry.getName()));
                if (entry.isDirectory()) {
                    destFile.mkdirs();
                    continue;
                }

                OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(destFile));
                try {
                    copyInputStream(zipFile.getInputStream(entry), outputStream);
                } finally {
                    outputStream.close();
                }
                //TODO add correct number
                chmod.chmod(destFile, 0755);
            }
        } finally {
            zipFile.close();
        }
    }

    private static void copyInputStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }

        in.close();
        out.close();
    }
}
