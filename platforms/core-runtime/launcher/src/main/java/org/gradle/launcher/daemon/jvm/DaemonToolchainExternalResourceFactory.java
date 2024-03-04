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

import org.gradle.api.credentials.Credentials;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationInternal;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.resource.DefaultExternalResourceRepository;
import org.gradle.internal.resource.ExternalResourceFactory;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.local.FileResourceConnector;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.gradle.internal.resource.transport.http.DefaultSslContextFactory;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.resource.transport.http.HttpConnectorFactory;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainHttpRedirectVerifierFactory;

import java.net.URI;
import java.util.Collection;

public class DaemonToolchainExternalResourceFactory implements ExternalResourceFactory {

    private final FileSystem fileSystem;
    private final ListenerManager listenerManager;
    private final JavaToolchainHttpRedirectVerifierFactory httpRedirectVerifierFactory;

    public DaemonToolchainExternalResourceFactory(FileSystem fileSystem, ListenerManager listenerManager, JavaToolchainHttpRedirectVerifierFactory httpRedirectVerifierFactory) {
        this.fileSystem = fileSystem;
        this.listenerManager = listenerManager;
        this.httpRedirectVerifierFactory = httpRedirectVerifierFactory;
    }

    @Override
    public ExternalResourceRepository createExternalResource(URI source, Collection<Authentication> authentications) {
        if ("file".equals(source.getScheme())) {
            return new FileResourceConnector(fileSystem, listenerManager);
        } else {
            final HttpRedirectVerifier redirectVerifier = httpRedirectVerifierFactory.createVerifier(source);
            ResourceConnectorSpecification connectionDetails = new DefaultResourceConnectorSpecification(authentications, redirectVerifier);
            ResourceConnectorFactory connectorFactory = new HttpConnectorFactory( new DefaultSslContextFactory(), HttpClientHelper.Factory.createFactory(null)); //findConnectorFactory(schemes);
            ExternalResourceConnector resourceConnector = connectorFactory.createResourceConnector(connectionDetails);

            // TODO add logging ?
            return new DefaultExternalResourceRepository("https", resourceConnector, resourceConnector, resourceConnector);
        }
    }

    // TODO COPIED
    private static class DefaultResourceConnectorSpecification implements ResourceConnectorSpecification {
        private final Collection<Authentication> authentications;
        private final HttpRedirectVerifier redirectVerifier;

        private DefaultResourceConnectorSpecification(Collection<Authentication> authentications, HttpRedirectVerifier redirectVerifier) {
            this.authentications = authentications;
            this.redirectVerifier = redirectVerifier;
        }

        @Override
        public <T> T getCredentials(Class<T> type) {
            if (authentications == null || authentications.size() < 1) {
                return null;
            }

            Credentials credentials = ((AuthenticationInternal) authentications.iterator().next()).getCredentials();

            if (credentials == null) {
                return null;
            }
            if (type.isAssignableFrom(credentials.getClass())) {
                return type.cast(credentials);
            } else {
                throw new IllegalArgumentException(String.format("Credentials must be an instance of '%s'.", type.getCanonicalName()));
            }
        }

        @Override
        public Collection<Authentication> getAuthentications() {
            return authentications;
        }

        @Override
        public HttpRedirectVerifier getRedirectVerifier() {
            return redirectVerifier;
        }
    }
}
