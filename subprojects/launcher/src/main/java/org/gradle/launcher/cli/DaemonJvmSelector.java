/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.launcher.cli;

import com.google.common.collect.Lists;
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.authentication.Authentication;
import org.gradle.cache.ObjectHolder;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.FileBackedObjectHolder;
import org.gradle.cache.internal.FileIntegrityViolationSuppressingObjectHolderDecorator;
import org.gradle.cache.internal.OnDemandFileAccess;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.CachingJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadataComparator;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JavaToolchain;
import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.MavenToolchainsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainDownloadFailedException;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory;
import org.gradle.launcher.daemon.registry.DaemonRegistryContent;
import org.gradle.platform.BuildPlatform;
import org.gradle.platform.internal.DefaultBuildPlatform;
import org.gradle.process.internal.ExecFactory;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Daemon JVM auto-detection implementation for use in the launcher.
 */
public class DaemonJvmSelector {

    private final ExecFactory execHandleFactory;
    private final WindowsRegistry windowsRegistry;
    private final CachingJvmMetadataDetector detector;
    private final ServiceRegistry basicServices;
    private final DaemonJvmInstallationMetadataCache cache;

    public DaemonJvmSelector(
        ExecFactory execHandleFactory,
        TemporaryFileProvider temporaryFileProvider,
        WindowsRegistry windowsRegistry,
        ServiceRegistry basicServices
    ) {
        this.execHandleFactory = execHandleFactory;
        this.windowsRegistry = windowsRegistry;
        this.basicServices = basicServices;
        // TODO: This cache probably doesn't do much in the launcher. We should be caching something to disk here so ideally we
        // can skip the entire detection process
        this.detector = new CachingJvmMetadataDetector(
            new DefaultJvmMetadataDetector(execHandleFactory, temporaryFileProvider));
        this.cache = new DaemonJvmInstallationMetadataCache(
            basicServices.get(CacheFactory.class),
            basicServices.get(GradleUserHomeDirProvider.class)
        );
    }

    public JvmInstallationMetadata getDaemonJvmInstallation(int version, Parameters parameters) {
        // Check if version exist on the detector to skip completely the discovery process
        JvmInstallationMetadata jvmInstallationMetadata = cache.getJvmInstallationMetadata(JavaVersion.toVersion(version));
        // TODO Validate if the home is still valid
        if (jvmInstallationMetadata != null) {
            return jvmInstallationMetadata;
        }
        // remove this
//        Optional<JvmInstallationMetadata> cacheJvmInstallation = detector.getJavaMetadata()
//            .values().stream()
//            .filter(test -> test.getLanguageVersion().equals(JavaVersion.toVersion(version)))
//            .findFirst();
//        if (cacheJvmInstallation.isPresent()) {
//            return cacheJvmInstallation.get();
//        }
        Optional<JvmInstallationMetadata> installation = getInstallation(
            parameters,
            it -> it.getLanguageVersion().equals(JavaVersion.toVersion(version)) // TODO equals
        );
        // TODO check when not present
        if (!installation.isPresent()) {
            // TODO download JDK
            // TODO duplicate logic
            System.out.println("DOWNLOADING JDK...");
            List<InstallationSupplier> installationSuppliers = getInstallationSuppliers(
                new BasicProviderFactory(parameters), execHandleFactory, windowsRegistry);
            JavaInstallationRegistry registry = new JavaInstallationRegistry(installationSuppliers, detector, null, OperatingSystem.current(), new NoOpProgressLoggerFactory());
            FileFactory fileFactory = basicServices.get(FileFactory.class);
            ObjectFactory objectFactory = basicServices.get(ObjectFactory.class);
            SystemInfo systemInfo = basicServices.get(SystemInfo.class);
            OperatingSystem operatingSystem = basicServices.get(OperatingSystem.class);
            BuildPlatform buildPlatform = new DefaultBuildPlatform(systemInfo, operatingSystem);
            JavaToolchainProvisioningService provisioningService = new DaemonJavaToolchainProvisioningService();
            JavaToolchainQueryService javaToolchainQueryService = new JavaToolchainQueryService(
                registry,
                detector,
                fileFactory,
                provisioningService,
                objectFactory,
                buildPlatform
            );
            // probably go directly to download

            // This is just a default JavaToolchainSpec {languageVersion=unspecified, vendor=any, implementation=vendor-specific}
            DefaultToolchainSpec javaToolchainSpec = basicServices.get(ObjectFactory.class).newInstance(DefaultToolchainSpec.class);
            javaToolchainSpec.getLanguageVersion().set(JavaLanguageVersion.of(19));
            javaToolchainSpec.getVendor().set(JvmVendorSpec.AZUL);
            JavaToolchain javaToolchain = javaToolchainQueryService.downloadToolchain(javaToolchainSpec);
            try {
                return javaToolchain.getMetadata();
            } catch (Exception exception) {
                throw new GradleException("Unable to download JVM installation found compatible with Java " + version + ".");
            }
        }
        cache.putJvmInstallationMetadata(JavaVersion.toVersion(version), installation.get());
        return installation.get();
    }

    class DaemonJavaToolchainProvisioningService implements JavaToolchainProvisioningService {

        @Override
        public File tryInstall(JavaToolchainSpec spec) throws ToolchainDownloadFailedException {
//            return provisionInstallation(spec, URI.create("https://cdn.azul.com/zulu/bin/zulu17.42.19-ca-jdk17.0.7-macosx_aarch64.zip"), Collections.emptyList());
            return provisionInstallation(spec, URI.create("https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jdk19.0.2-macosx_aarch64.zip"), Collections.emptyList());
        }

        @Override
        public boolean isAutoDownloadEnabled() {
            return true;
        }

        @Override
        public boolean hasConfiguredToolchainRepositories() {
            return true;
        }
    }

    private Optional<JvmInstallationMetadata> getInstallation(Parameters parameters, Predicate<? super JvmInstallationMetadata> criteria) {

        List<InstallationSupplier> installationSuppliers = getInstallationSuppliers(
            new BasicProviderFactory(parameters), execHandleFactory, windowsRegistry);
        JavaInstallationRegistry registry = new JavaInstallationRegistry(installationSuppliers, detector, null, OperatingSystem.current(), new NoOpProgressLoggerFactory());

        // TODO: What are the performance implications of doing this in the launcher?
        // Probably not good.
        return registry.listInstallations().stream()
            .map(detector::getMetadata)
            .filter(JvmInstallationMetadata::isValidInstallation)
            .filter(criteria)
            .min(new JvmInstallationMetadataComparator(Jvm.current().getJavaHome()));
    }

    // TODO: We should standardize our installation suppliers across AvailableJavaHomes, this, and PlatformJvmServices.
    private List<InstallationSupplier> getInstallationSuppliers(ProviderFactory providerFactory, ExecFactory execFactory, WindowsRegistry windowsRegistry) {
        // TODO: Also leverage the AutoInstalledInstallationSupplier by moving it to a
        // subproject accessible from here. This will require moving a few other things around.
        FileResolver fileResolver = basicServices.get(FileLookup.class).getFileResolver();
//        FileCollectionFactory fileCollectionFactory = basicServices.get(FileCollectionFactory.class).withResolver(fileResolver);
//        FileLockManager fileLockManager = basicServices.get(FileLockManager.class);

        GradleUserHomeDirProvider gradleUserHomeDirProvider = basicServices.get(GradleUserHomeDirProvider.class);
//        FileOperations fileOperations = DefaultFileOperations.createSimple(fileResolver, fileCollectionFactory, basicServices);
        FileResolver resolver = new IdentityFileResolver();
        return Lists.newArrayList(
            new AsdfInstallationSupplier(providerFactory),
            new CurrentInstallationSupplier(providerFactory),
            new EnvironmentVariableListInstallationSupplier(providerFactory, resolver),
            new IntellijInstallationSupplier(providerFactory, resolver),
            new JabbaInstallationSupplier(providerFactory),
            new LinuxInstallationSupplier(providerFactory),
            new LocationListInstallationSupplier(providerFactory, resolver),
            new MavenToolchainsInstallationSupplier(providerFactory, resolver),
            new OsXInstallationSupplier(execFactory, providerFactory, OperatingSystem.current()),
            new SdkmanInstallationSupplier(providerFactory),
            new WindowsInstallationSupplier(windowsRegistry, OperatingSystem.current(), providerFactory),
            // TODO move AutoInstalledInstallationSupplier accessible from there
            // TODO JdkCacheDirectory extract operation and lockManager
            new AutoInstalledInstallationSupplier(providerFactory, new JdkCacheDirectory(gradleUserHomeDirProvider, null, null, detector))
        );
    }

    private File provisionInstallation(JavaToolchainSpec spec, URI uri, Collection<Authentication> authentications) {
        synchronized(this) {
            try {
                GradleUserHomeDirProvider gradleUserHomeDirProvider = basicServices.get(GradleUserHomeDirProvider.class);
                // TODO maybe we can use JdkCacheDirectory.getDownloadLocation();
                File downloadFolder = new File(gradleUserHomeDirProvider.getGradleUserHomeDirectory(), "jdks");
//                File archiveFile = new File(downloadFolder, "zulu17.42.19-ca-jdk17.0.7-macosx_aarch64.zip"); //TODO obtina file name
                File archiveFile = new File(downloadFolder, "zulu19.32.13-ca-jdk19.0.2-macosx_aarch64.zip"); //TODO obtina file name
                new Download(new Logger(false), "Gradle Tooling API", GradleVersion.current().getVersion()).download(uri, archiveFile);
                return provisionFromArchive(spec, downloadFolder, archiveFile, uri); //cacheDirProvider.provisionFromArchive(spec, archiveFile, uri);
                // copied from DefaultJavaToolchainProvisioningService
//                ExternalResource resource = wrapInOperation("Examining toolchain URI " + uri, () -> downloader.getResourceFor(uri, authentications));
//                File archiveFile = new File(downloadFolder, getFileName(uri, resource));
//                final FileLock fileLock = cacheDirProvider.acquireWriteLock(archiveFile, "Downloading toolchain");
//                try {
//                    if (!archiveFile.exists()) {
//                        wrapInOperation("Downloading toolchain from URI " + uri, () -> {
//                            downloader.download(uri, archiveFile, resource);
//                            return null;
//                        });
//                    }
//                    return wrapInOperation("Unpacking toolchain archive " + archiveFile.getName(), () -> cacheDirProvider.provisionFromArchive(spec, archiveFile, uri));
//                } finally {
//                    fileLock.close();
//                }
            } catch (Exception e) {
                //throw new DefaultJavaToolchainProvisioningService.MissingToolchainException(spec, uri, e);
                throw new GradleException("Unable to download JVM installation found compatible with Java " + e + ".");
            }
        }
    }

    private File provisionFromArchive(JavaToolchainSpec spec, File downloadFolder, File archiveFile, URI uri) {
        try {
            unzip(archiveFile, downloadFolder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //File result = getJavaHome(new File(downloadFolder, "zulu17.42.19-ca-jdk17.0.7-macosx_aarch64"));
        File result = getJavaHome(new File(downloadFolder, "zulu19.32.13-ca-jdk19.0.2-macosx_aarch64"));
        setExecutablePermissions(new File(result, "/bin/java"));
        return result;
    }

    private void setExecutablePermissions(File gradleHome) {
        String errorMessage = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("chmod", "755", gradleHome.getCanonicalPath());
            Process p = pb.start();
            if (p.waitFor() != 0) {
                BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
                Formatter stdout = new Formatter();
                String line;
                while ((line = is.readLine()) != null) {
                    stdout.format("%s%n", line);
                }
                errorMessage = stdout.toString();
            }
        } catch (IOException e) {
            errorMessage = e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorMessage = e.getMessage();
        }
    }

    private void unzip(File zip, File dest) throws IOException {
        ZipFile zipFile = new ZipFile(zip);
        try {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                File destFile = new File(dest, entry.getName());
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
            }
        } finally {
            zipFile.close();
        }
    }

    private void copyInputStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }

        in.close();
        out.close();
    }

    private JvmInstallationMetadata getMetadata(File markedLocation) {
        File javaHome = getJavaHome(markedLocation);

        JvmInstallationMetadata metadata = detector.getMetadata(new InstallationLocation(javaHome, "provisioned toolchain", true));
        if (!metadata.isValidInstallation()) {
            throw new GradleException("Provisioned toolchain '" + javaHome + "' could not be probed: " + metadata.getErrorMessage(), metadata.getErrorCause());
        }

        return metadata;
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
            .toLowerCase();
    }

    private File getJavaHome(File markedLocation) {
        if (OperatingSystem.current().isMacOsX()) {
            if (new File(markedLocation, "Contents/Home").exists()) {
                return new File(markedLocation, "Contents/Home");
            }

            File[] subfolders = markedLocation.listFiles(File::isDirectory);
            if (subfolders != null) {
                for(File subfolder : subfolders) {
                    if (new File(subfolder, "Contents/Home").exists()) {
                        return new File(subfolder, "Contents/Home");
                    }
                }
            }
        }

        return markedLocation;
    }

    private String getFileName(URI uri, ExternalResource resource) {
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

    /**
     * A {@link org.gradle.api.provider.ProviderFactory} which only provides
     * environment variables, system properties, and Gradle properties. A fully featured
     * provider factory is not otherwise available at this service scope.
     */
    private static class BasicProviderFactory extends DefaultProviderFactory {

        private final Parameters parameters;

        public BasicProviderFactory(Parameters parameters) {
            this.parameters = parameters;
        }

        @Override
        public Provider<String> environmentVariable(Provider<String> variableName) {
            return variableName.map(System::getenv);
        }

        @Override
        public Provider<String> systemProperty(Provider<String> propertyName) {
            return propertyName.map(this::getProperty);
        }

        @Override
        public Provider<String> gradleProperty(Provider<String> propertyName) {
            return propertyName.map(this::getProperty);
        }

        @Nullable
        private String getProperty(String key) {
            String val = System.getProperty(key);
            if (val != null) {
                return val;
            }
            val = parameters.getStartParameter().getProjectProperties().get(key);
            if (val != null) {
                return val;
            }
            return parameters.getStartParameter().getSystemPropertiesArgs().get(key);
        }
    }

    static class NoOpProgressLoggerFactory implements ProgressLoggerFactory {
        @Override
        public ProgressLogger newOperation(String loggerCategory) {
            return new Logger();
        }

        @Override
        public ProgressLogger newOperation(Class<?> loggerCategory) {
            return new Logger();
        }

        @Override
        public ProgressLogger newOperation(Class<?> loggerCategory, BuildOperationDescriptor buildOperationDescriptor) {
            return new Logger();
        }

        public ProgressLogger newOperation(Class<?> loggerClass, ProgressLogger parent) {
            return new Logger();
        }

        static class Logger implements ProgressLogger {
            String description;

            public String getDescription() {return description;}

            public ProgressLogger setDescription(String description) {
                this.description = description;
                return this;
            }

            public ProgressLogger start(String description, String status) {
                setDescription(description);
                started();
                return this;
            }

            public void started() {}
            public void started(String status) {}
            public void progress(String status) {}
            public void progress(String status, boolean failing) {}
            public void completed() {}
            public void completed(String status, boolean failed) {}
        }
    }

    public interface IDownload {
        void download(URI address, File destination) throws Exception;
    }

    public interface DownloadProgressListener {
        /**
         * Reports the current progress of the download
         *
         * @param address       distribution url
         * @param contentLength the content length of the distribution, or -1 if the content length is not known.
         * @param downloaded    the total amount of currently downloaded bytes
         */
        void downloadStatusChanged(URI address, long contentLength, long downloaded);
    }


    public class Logger implements Appendable {

        private final boolean quiet;

        public Logger(boolean quiet) {
            this.quiet = quiet;
        }

        public void log(String message) {
            if (!quiet) {
                System.out.println(message);
            }
        }

        public Appendable append(CharSequence csq) {
            if (!quiet) {
                System.out.append(csq);
            }
            return this;
        }

        public Appendable append(CharSequence csq, int start, int end) {
            if (!quiet) {
                System.out.append(csq, start, end);
            }
            return this;
        }

        public Appendable append(char c) {
            if(!quiet) {
                System.out.append(c);
            }
            return this;
        }
    }

    public static class Download implements IDownload {
        public static final String UNKNOWN_VERSION = "0";
        public static final int DEFAULT_NETWORK_TIMEOUT_MILLISECONDS = 10 * 1000;

        private static final int BUFFER_SIZE = 10 * 1024;
        private static final int PROGRESS_CHUNK = 1024 * 1024;
        private final Logger logger;
        private final String appName;
        private final String appVersion;
        private final DownloadProgressListener progressListener;
        private final Map<String, String> systemProperties;
        private final int networkTimeout;

        public Download(Logger logger, String appName, String appVersion) {
            this(logger, null, appName, appVersion, convertSystemProperties(System.getProperties()));
        }

        public Download(Logger logger, String appName, String appVersion, int networkTimeout) {
            this(logger, null, appName, appVersion, convertSystemProperties(System.getProperties()), networkTimeout);
        }

        private static Map<String, String> convertSystemProperties(Properties properties) {
            Map<String, String> result = new HashMap<String, String>();
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue() == null ? null : entry.getValue().toString());
            }
            return result;
        }

        public Download(Logger logger, DownloadProgressListener progressListener, String appName, String appVersion, Map<String, String> systemProperties) {
            this(logger, progressListener, appName, appVersion, systemProperties, DEFAULT_NETWORK_TIMEOUT_MILLISECONDS);
        }

        public Download(Logger logger, DownloadProgressListener progressListener, String appName, String appVersion, Map<String, String> systemProperties, int networkTimeout) {
            this.logger = logger;
            this.appName = appName;
            this.appVersion = appVersion;
            this.systemProperties = systemProperties;
            this.progressListener = new DefaultDownloadProgressListener(logger, progressListener);
            this.networkTimeout = networkTimeout;
            configureProxyAuthentication();
        }

        private void configureProxyAuthentication() {
            if (systemProperties.get("http.proxyUser") != null || systemProperties.get("https.proxyUser") != null) {
                // Only an authenticator for proxies needs to be set. Basic authentication is supported by directly setting the request header field.
                Authenticator.setDefault(new ProxyAuthenticator());
            }
        }

        public void sendHeadRequest(URI uri) throws Exception {
            URL safeUrl = safeUri(uri).toURL();
            int responseCode = -1;
            try {
                HttpURLConnection conn = (HttpURLConnection)safeUrl.openConnection();
                conn.setRequestMethod("HEAD");
                addBasicAuthentication(uri, conn);
                conn.setRequestProperty("User-Agent", calculateUserAgent());
                conn.setConnectTimeout(networkTimeout);
                conn.connect();
                responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    throw new RuntimeException("HEAD request to " + safeUrl + " failed: response code (" + responseCode + ")");
                }
            } catch (IOException e) {
                throw new RuntimeException("HEAD request to " + safeUrl + " failed: response code (" + responseCode + "), timeout (" + networkTimeout + "ms)", e);
            }
        }

        public void download(URI address, File destination) throws Exception {
            destination.getParentFile().mkdirs();
            downloadInternal(address, destination);
        }

        private void downloadInternal(URI address, File destination)
            throws Exception {
            OutputStream out = null;
            URLConnection conn;
            InputStream in = null;
            URL safeUrl = safeUri(address).toURL();
            try {
                out = new BufferedOutputStream(new FileOutputStream(destination));

                // No proxy is passed here as proxies are set globally using the HTTP(S) proxy system properties. The respective protocol handler implementation then makes use of these properties.
                conn = safeUrl.openConnection();

                addBasicAuthentication(address, conn);
                final String userAgentValue = calculateUserAgent();
                conn.setRequestProperty("User-Agent", userAgentValue);
                conn.setConnectTimeout(networkTimeout);
                conn.setReadTimeout(networkTimeout);
                in = conn.getInputStream();
                byte[] buffer = new byte[BUFFER_SIZE];
                int numRead;
                int totalLength = conn.getContentLength();
                long downloadedLength = 0;
                long progressCounter = 0;
                while ((numRead = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Download was interrupted.");
                    }

                    downloadedLength += numRead;
                    progressCounter += numRead;

                    if (progressCounter / PROGRESS_CHUNK > 0 || downloadedLength == totalLength) {
                        progressCounter = progressCounter - PROGRESS_CHUNK;
                        progressListener.downloadStatusChanged(address, totalLength, downloadedLength);
                    }

                    out.write(buffer, 0, numRead);
                }
            } catch (SocketTimeoutException e) {
                throw new IOException("Downloading from " + safeUrl + " failed: timeout (" + networkTimeout + "ms)", e);
            } finally {
                logger.log("");
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            }
        }

        /**
         * Create a safe URI from the given one by stripping out user info.
         *
         * @param uri Original URI
         * @return a new URI with no user info
         */
        URI safeUri(URI uri) {
            try {
                return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
            } catch (URISyntaxException e) {
                throw new RuntimeException("Failed to parse URI", e);
            }
        }

        private void addBasicAuthentication(URI address, URLConnection connection) throws IOException {
            String userInfo = calculateUserInfo(address);
            if (userInfo == null) {
                return;
            }
            if (!"https".equals(address.getScheme())) {
                logger.log("WARNING Using HTTP Basic Authentication over an insecure connection to download the Gradle distribution. Please consider using HTTPS.");
            }
            connection.setRequestProperty("Authorization", "Basic " + base64Encode(userInfo));
        }

        /**
         * Base64 encode user info for HTTP Basic Authentication.
         *
         * Try to use {@literal java.util.Base64} encoder which is available starting with Java 8.
         * Fallback to {@literal javax.xml.bind.DatatypeConverter} from JAXB which is available starting with Java 6 but is not anymore in Java 9.
         * Fortunately, both of these two Base64 encoders implement the right Base64 flavor, the one that does not split the output in multiple lines.
         *
         * @param userInfo user info
         * @return Base64 encoded user info
         * @throws RuntimeException if no public Base64 encoder is available on this JVM
         */
        private String base64Encode(String userInfo) {
            ClassLoader loader = getClass().getClassLoader();
            try {
                Method getEncoderMethod = loader.loadClass("java.util.Base64").getMethod("getEncoder");
                Method encodeMethod = loader.loadClass("java.util.Base64$Encoder").getMethod("encodeToString", byte[].class);
                Object encoder = getEncoderMethod.invoke(null);
                return (String) encodeMethod.invoke(encoder, new Object[]{userInfo.getBytes("UTF-8")});
            } catch (Exception java7OrEarlier) {
                try {
                    Method encodeMethod = loader.loadClass("javax.xml.bind.DatatypeConverter").getMethod("printBase64Binary", byte[].class);
                    return (String) encodeMethod.invoke(null, new Object[]{userInfo.getBytes("UTF-8")});
                } catch (Exception java5OrEarlier) {
                    throw new RuntimeException("Downloading Gradle distributions with HTTP Basic Authentication is not supported on your JVM.", java5OrEarlier);
                }
            }
        }

        private String calculateUserInfo(URI uri) {
            String username = systemProperties.get("gradle.wrapperUser");
            String password = systemProperties.get("gradle.wrapperPassword");
            if (username != null && password != null) {
                return username + ':' + password;
            }
            return uri.getUserInfo();
        }

        private String calculateUserAgent() {
            String javaVendor = systemProperties.get("java.vendor");
            String javaVersion = systemProperties.get("java.version");
            String javaVendorVersion = systemProperties.get("java.vm.version");
            String osName = systemProperties.get("os.name");
            String osVersion = systemProperties.get("os.version");
            String osArch = systemProperties.get("os.arch");
            return String.format("%s/%s (%s;%s;%s) (%s;%s;%s)", appName, appVersion, osName, osVersion, osArch, javaVendor, javaVersion, javaVendorVersion);
        }

        private class ProxyAuthenticator extends Authenticator {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    // Note: Do not use getRequestingProtocol() here, which is "http" even for HTTPS proxies.
                    String protocol = getRequestingURL().getProtocol();
                    String proxyUser = systemProperties.get(protocol + ".proxyUser");
                    if (proxyUser != null) {
                        String proxyPassword = systemProperties.get(protocol + ".proxyPassword");
                        if (proxyPassword == null) {
                            proxyPassword = "";
                        }
                        return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                    }
                }

                return super.getPasswordAuthentication();
            }
        }

        private static class DefaultDownloadProgressListener implements DownloadProgressListener {
            private final Logger logger;
            private final DownloadProgressListener delegate;
            private int previousDownloadPercent;

            public DefaultDownloadProgressListener(Logger logger, DownloadProgressListener delegate) {
                this.logger = logger;
                this.delegate = delegate;
                this.previousDownloadPercent = 0;
            }

            @Override
            public void downloadStatusChanged(URI address, long contentLength, long downloaded) {
                // If the total size of distribution is known, but there's no advanced progress listener, provide extra progress information
                if (contentLength > 0 && delegate == null) {
                    appendPercentageSoFar(contentLength, downloaded);
                }

                if (contentLength != downloaded) {
                    logger.append(".");
                }

                if (delegate != null) {
                    delegate.downloadStatusChanged(address, contentLength, downloaded);
                }
            }

            private void appendPercentageSoFar(long contentLength, long downloaded) {
                try {
                    int currentDownloadPercent = 10 * (calculateDownloadPercent(contentLength, downloaded) / 10);
                    if (currentDownloadPercent != 0 && previousDownloadPercent != currentDownloadPercent) {
                        logger.append(String.valueOf(currentDownloadPercent)).append('%');
                        previousDownloadPercent = currentDownloadPercent;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            private int calculateDownloadPercent(long totalLength, long downloadedLength) {
                return Math.min(100, Math.max(0, (int) ((downloadedLength / (double) totalLength) * 100)));
            }
        }
    }
}
