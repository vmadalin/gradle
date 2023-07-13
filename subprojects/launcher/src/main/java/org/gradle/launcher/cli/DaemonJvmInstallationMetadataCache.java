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

import org.gradle.api.JavaVersion;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.DefaultCacheFactory;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.Factory;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DaemonJvmInstallationMetadataCache {

    private final PersistentCache persistentCache;
    private final IndexedCache<JavaVersion, JvmInstallationMetadata> indexedCache;

    DaemonJvmInstallationMetadataCache(CacheFactory cacheFactory, GradleUserHomeDirProvider gradleUserHomeDirProvider) {
        File rootDir = new File(gradleUserHomeDirProvider.getGradleUserHomeDirectory(), "jdks");
        DefaultCacheScopeMapping cacheScopeMapping = new DefaultCacheScopeMapping(rootDir, GradleVersion.current());
        File cacheBaseDir = cacheScopeMapping.getBaseDirectory(null, "daemonJvm", VersionStrategy.SharedCache);
        String displayName = "daemonJvm";
        CacheBuilder.LockTarget lockTarget = CacheBuilder.LockTarget.DefaultTarget;
        LockOptionsBuilder lockOptions = mode(FileLockManager.LockMode.None); // TODO locking mode

        persistentCache = cacheFactory.open(cacheBaseDir, displayName, Collections.emptyMap(), lockTarget, lockOptions, null, null);
        indexedCache = persistentCache.createIndexedCache("daemonJvm", JavaVersion.class, new DefaultSerializer<JvmInstallationMetadata>());
    }

    public @Nullable JvmInstallationMetadata getJvmInstallationMetadata(JavaVersion javaVersion) {
        return persistentCache.withFileLock(() -> indexedCache.getIfPresent(javaVersion));
    }

    public void putJvmInstallationMetadata(JavaVersion javaVersion, JvmInstallationMetadata jvmInstallationMetadata) {
        persistentCache.withFileLock(() -> indexedCache.put(javaVersion, jvmInstallationMetadata));
    }
}
