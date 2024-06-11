/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.userinput.UserInputReader;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Actions;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.agents.AgentInitializer;
import org.gradle.internal.agents.AgentStatus;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.BasicGlobalScopeServices;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.daemon.bootstrap.ForegroundDaemonAction;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices;
import org.gradle.launcher.daemon.client.DaemonStopClient;
import org.gradle.launcher.daemon.client.ReportDaemonStatusClient;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.configuration.ForegroundDaemonConfiguration;
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonRequestContext;
import org.gradle.launcher.daemon.context.DefaultDaemonContext;
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildExecuter;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.process.internal.CurrentProcess;

import java.lang.management.ManagementFactory;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

class BuildActionsFactory implements CommandLineActionCreator {
    private final BuildEnvironmentConfigurationConverter buildEnvironmentConfigurationConverter;
    private final ServiceRegistry loggingServices;
    private final JvmVersionDetector jvmVersionDetector;
    private final FileCollectionFactory fileCollectionFactory;
    private final ServiceRegistry basicServices;

    public BuildActionsFactory(ServiceRegistry loggingServices) {
        this.basicServices = ServiceRegistryBuilder.builder()
            .scope(Scope.Global.class)
            .displayName("Basic global services")
            .parent(loggingServices)
            .parent(NativeServices.getInstance())
            .provider(new BasicGlobalScopeServices())
            .build();
        this.loggingServices = loggingServices;
        this.fileCollectionFactory = basicServices.get(FileCollectionFactory.class);
        this.buildEnvironmentConfigurationConverter = new BuildEnvironmentConfigurationConverter(
            new BuildLayoutFactory(),
            fileCollectionFactory);
        this.jvmVersionDetector = basicServices.get(JvmVersionDetector.class);
    }

    @Override
    public void configureCommandLineParser(CommandLineParser parser) {
        buildEnvironmentConfigurationConverter.configure(parser);
    }

    @Override
    public Action<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
        Parameters parameters = buildEnvironmentConfigurationConverter.convertParameters(commandLine, null);

        StartParameterInternal startParameter = parameters.getStartParameter();
        DaemonParameters daemonParameters = parameters.getDaemonParameters();
        AllProperties allProperties = parameters.getProperties();

        if (daemonParameters.isStop()) {
            return Actions.toAction(stopAllDaemons(daemonParameters));
        }
        if (daemonParameters.isStatus()) {
            return Actions.toAction(showDaemonStatus(daemonParameters));
        }
        if (daemonParameters.isForeground()) {
            ForegroundDaemonConfiguration conf = new ForegroundDaemonConfiguration(
                UUID.randomUUID().toString(), daemonParameters.getBaseDir(), daemonParameters.getIdleTimeout(), daemonParameters.getPeriodicCheckInterval(), fileCollectionFactory,
                daemonParameters.shouldApplyInstrumentationAgent(), daemonParameters.getNativeServicesMode());
            return Actions.toAction(new ForegroundDaemonAction(loggingServices, conf));
        }

        DaemonRequestContext requestContext = configureForRequestContext(daemonParameters);
        if (daemonParameters.isEnabled()) {
            return Actions.toAction(runBuildWithDaemon(startParameter, daemonParameters, requestContext));
        }
        if (canUseCurrentProcess(daemonParameters, requestContext)) {
            return Actions.toAction(runBuildInProcess(startParameter, daemonParameters));
        }

        return Actions.toAction(runBuildInSingleUseDaemon(startParameter, daemonParameters, requestContext));
    }

    private DaemonRequestContext configureForRequestContext(DaemonParameters daemonParameters) {
        // Gradle daemon properties have been defined
        if (daemonParameters.getRequestedJvmCriteria() != null) {
            DaemonJvmCriteria criteria = daemonParameters.getRequestedJvmCriteria();
            daemonParameters.applyDefaultsFor(JavaVersion.toVersion(criteria.getJavaVersion()));
            return new DaemonRequestContext(daemonParameters.getRequestedJvmBasedOnJavaHome(), daemonParameters.getRequestedJvmCriteria(), daemonParameters.getEffectiveJvmArgs(), daemonParameters.shouldApplyInstrumentationAgent(), daemonParameters.getNativeServicesMode(), daemonParameters.getPriority());
        } else if (daemonParameters.getRequestedJvmBasedOnJavaHome() != null && daemonParameters.getRequestedJvmBasedOnJavaHome() != Jvm.current()) {
            // Either the TAPI client or org.gradle.java.home has been provided
            JavaVersion detectedVersion = jvmVersionDetector.getJavaVersion(daemonParameters.getRequestedJvmBasedOnJavaHome());
            daemonParameters.applyDefaultsFor(detectedVersion);
            return new DaemonRequestContext(daemonParameters.getRequestedJvmBasedOnJavaHome(), daemonParameters.getRequestedJvmCriteria(), daemonParameters.getEffectiveJvmArgs(), daemonParameters.shouldApplyInstrumentationAgent(), daemonParameters.getNativeServicesMode(), daemonParameters.getPriority());
        } else {
            daemonParameters.applyDefaultsFor(JavaVersion.current());
            return new DaemonRequestContext(Jvm.current(), daemonParameters.getRequestedJvmCriteria(), daemonParameters.getEffectiveJvmArgs(), daemonParameters.shouldApplyInstrumentationAgent(), daemonParameters.getNativeServicesMode(), daemonParameters.getPriority());
        }
    }

    private Runnable stopAllDaemons(DaemonParameters daemonParameters) {
        ServiceRegistry clientSharedServices = createGlobalClientServices(false);
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createMessageDaemonServices(loggingServices, daemonParameters);
        DaemonStopClient stopClient = clientServices.get(DaemonStopClient.class);
        return new StopDaemonAction(stopClient);
    }

    private Runnable showDaemonStatus(DaemonParameters daemonParameters) {
        ServiceRegistry clientSharedServices = createGlobalClientServices(false);
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createMessageDaemonServices(loggingServices, daemonParameters);
        ReportDaemonStatusClient statusClient = clientServices.get(ReportDaemonStatusClient.class);
        return new ReportDaemonStatusAction(statusClient);
    }

    private Runnable runBuildWithDaemon(StartParameterInternal startParameter, DaemonParameters daemonParameters, DaemonRequestContext requestContext) {
        // Create a client that will match based on the daemon startup parameters.
        ServiceRegistry clientSharedServices = createGlobalClientServices(true);
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createBuildClientServices(loggingServices, daemonParameters, requestContext, System.in, Optional.empty());
        DaemonClient client = clientServices.get(DaemonClient.class);
        return runBuildAndCloseServices(startParameter, daemonParameters, client, clientSharedServices, clientServices);
    }

    protected boolean canUseCurrentProcess(DaemonParameters daemonParameters, DaemonRequestContext requestContext) {
        // Pretend like the current process is actually a daemon, and see if it satisfies the compatibility spec
        CurrentProcess currentProcess = new CurrentProcess(fileCollectionFactory);
        DaemonContext contextForCurrentProcess = buildDaemonContextForCurrentProcess(requestContext, currentProcess);

        DaemonCompatibilitySpec comparison = new DaemonCompatibilitySpec(requestContext);
        if (!currentProcess.isLowMemoryProcess()) {
            if (comparison.isSatisfiedBy(contextForCurrentProcess)) {
                if (daemonParameters.hasUserDefinedImmutableJvmArgs()) {
                    return currentProcess.getJvmOptions().getAllImmutableJvmArgs().equals(daemonParameters.getEffectiveSingleUseJvmArgs());
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    @VisibleForTesting
    static DaemonContext buildDaemonContextForCurrentProcess(DaemonRequestContext requestContext, CurrentProcess currentProcess) {
        return new DefaultDaemonContext(
            UUID.randomUUID().toString(),
            currentProcess.getJvm().getJavaHome(),
            JavaVersion.current(),
            JvmVendor.KnownJvmVendor.parse(Jvm.current().getVendor()),
            null, 0L, 0,
            // The gradle options aren't being properly checked.
            requestContext.getDaemonOpts(),
            AgentStatus.allowed().isAgentInstrumentationEnabled(),
            // These aren't being properly checked.
            // We assume the current process is compatible when considering these properties.
            requestContext.getNativeServicesMode(),
            requestContext.getPriority()
        );
    }

    private Runnable runBuildInProcess(StartParameterInternal startParameter, DaemonParameters daemonParameters) {
        // Set the system properties and use this process
        Properties properties = new Properties();
        properties.putAll(daemonParameters.getEffectiveSystemProperties());
        System.setProperties(properties);

        ServiceRegistry globalServices = ServiceRegistryBuilder.builder()
            .scope(Scope.Global.class)
            .displayName("Global services")
            .parent(loggingServices)
            .parent(NativeServices.getInstance())
            .provider(new GlobalScopeServices(startParameter.isContinuous(), AgentStatus.of(daemonParameters.shouldApplyInstrumentationAgent())))
            .build();

        globalServices.get(AgentInitializer.class).maybeConfigureInstrumentationAgent();

        BuildActionExecuter<BuildActionParameters, BuildRequestContext> executer = new InProcessUserInputHandlingExecutor(
            globalServices.get(GlobalUserInputReceiver.class),
            globalServices.get(UserInputReader.class),
            globalServices.get(BuildExecuter.class)
        );

        // Force the user home services to be stopped first, the dependencies between the user home services and the global services are not preserved currently
        return runBuildAndCloseServices(startParameter, daemonParameters, executer, globalServices, globalServices.get(GradleUserHomeScopeServiceRegistry.class));
    }

    private Runnable runBuildInSingleUseDaemon(StartParameterInternal startParameter, DaemonParameters daemonParameters, DaemonRequestContext requestContext) {
        //(SF) this is a workaround until this story is completed. I'm hardcoding setting the idle timeout to be max X mins.
        //this way we avoid potential runaway daemons that steal resources on linux and break builds on windows.
        //We might leave that in if we decide it's a good idea for an extra safety net.
        int maxTimeout = 2 * 60 * 1000;
        if (daemonParameters.getIdleTimeout() > maxTimeout) {
            daemonParameters.setIdleTimeout(maxTimeout);
        }
        //end of workaround.

        // Create a client that will not match any existing daemons, so it will always start a new one
        ServiceRegistry clientSharedServices = createGlobalClientServices(true);
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createSingleUseDaemonClientServices(clientSharedServices, daemonParameters, requestContext, System.in);
        DaemonClient client = clientServices.get(DaemonClient.class);
        return runBuildAndCloseServices(startParameter, daemonParameters, client, clientSharedServices, clientServices);
    }

    private ServiceRegistry createGlobalClientServices(boolean usingDaemon) {
        ServiceRegistryBuilder builder = ServiceRegistryBuilder.builder()
            .displayName("Daemon client global services")
            .parent(NativeServices.getInstance());
        if (usingDaemon) {
            builder.parent(basicServices);
        } else {
            builder.provider(new GlobalScopeServices(false, AgentStatus.disabled()));
        }
        return builder.provider(new DaemonClientGlobalServices()).build();
    }

    private Runnable runBuildAndCloseServices(StartParameterInternal startParameter, DaemonParameters daemonParameters, BuildActionExecuter<BuildActionParameters, BuildRequestContext> executer, ServiceRegistry sharedServices, Object... stopBeforeSharedServices) {
        BuildActionParameters parameters = createBuildActionParameters(startParameter, daemonParameters);
        Stoppable stoppable = new CompositeStoppable().add(stopBeforeSharedServices).add(sharedServices);
        return new RunBuildAction(executer, startParameter, clientMetaData(), getBuildStartTime(), parameters, sharedServices, stoppable);
    }

    private BuildActionParameters createBuildActionParameters(StartParameter startParameter, DaemonParameters daemonParameters) {
        return new DefaultBuildActionParameters(
            daemonParameters.getEffectiveSystemProperties(),
            daemonParameters.getEnvironmentVariables(),
            SystemProperties.getInstance().getCurrentDir(),
            startParameter.getLogLevel(),
            daemonParameters.isEnabled(),
            ClassPath.EMPTY);
    }

    private long getBuildStartTime() {
        return ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    private GradleLauncherMetaData clientMetaData() {
        return new GradleLauncherMetaData();
    }
}
