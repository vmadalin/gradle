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

package org.gradle.launcher.daemon.jvm;

import org.gradle.cache.FileLock;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.ToolchainDownloadFailedException;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

public class DaemonJavaToolchainProvisioningService implements JavaToolchainProvisioningService {

    private static final Object PROVISIONING_PROCESS_LOCK = new Object();

    private final SecureFileDownloader downloader;
    private final JdkCacheDirectory cacheDirProvider;

    public DaemonJavaToolchainProvisioningService(SecureFileDownloader downloader, JdkCacheDirectory cacheDirProvider) {
        this.downloader = downloader;
        this.cacheDirProvider = cacheDirProvider;
    }

    @Override
    public File tryInstall(JavaToolchainSpec spec) throws ToolchainDownloadFailedException {
        synchronized (PROVISIONING_PROCESS_LOCK) {
            try {
                // TODO read build properties
                // "https://api.foojay.io/disco/v3.0/ids/26710ac26a2d6a9fa53cb1ee13f01e14/redirect"
                // "https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jdk19.0.2-macosx_aarch64.zip"
                // "file:/Users/vmadalin/Downloads/zulu19.32.13-ca-jdk19.0.2-macosx_aarch64.zip"
                Properties properties = GUtil.loadProperties(new File("/Users/vmadalin/Downloads/sampleProject","gradle/gradle-build.properties"));
                String url = (String) properties.get("daemon.jvm.toolchain.macos.aarch64.url");
                URI uri = new URI(url);
                File downloadFolder = cacheDirProvider.getDownloadLocation();
                ExternalResource resource = downloader.getResourceFor(uri); // TODO logging
                File archiveFile = new File(downloadFolder, getFileName(uri, resource));
                final FileLock fileLock = cacheDirProvider.acquireWriteLock(archiveFile, "Downloading toolchain");
                try {
                    if (!archiveFile.exists()) {
                        // TODO add logging
                        downloader.download(uri, archiveFile, resource);
                    }
                    // TODO add logging
                    return cacheDirProvider.provisionFromArchive(spec, archiveFile, uri);
                } finally {
                    fileLock.close();
                }
            } catch (Exception e) {
                // TODO add uri
                try {
                    throw new MissingToolchainException(spec, new URI("https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jdk19.0.2-macosx_aarch64.zip"), new Throwable(e));
                } catch (URISyntaxException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        // TODO Review download failing exception
    }
}
