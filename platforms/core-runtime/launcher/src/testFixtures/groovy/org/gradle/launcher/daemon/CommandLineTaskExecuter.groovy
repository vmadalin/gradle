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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.executer.TaskExecuter

class CommandLineTaskExecuter implements TaskExecuter {

    private GradleExecuter gradleExecuter

    CommandLineTaskExecuter(GradleExecuter gradleExecuter) {
        this.gradleExecuter = gradleExecuter
    }

    @Override
    ExecutionResult executeTasks(boolean requireIsolatedUserHome, List<String> arguments, String... tasks) {
        def gradleTaskExecuter = gradleExecuter
            .withTasks(tasks)
            .withArguments(arguments)
            .requireIsolatedDaemons()

        if (requireIsolatedUserHome) {
            gradleTaskExecuter.requireOwnGradleUserHomeDir()
        }

        def gradle = gradleTaskExecuter.start()
        gradle.waitForExit()

        return OutputScrapingExecutionResult.from(gradle.standardOutput, gradle.errorOutput)
    }
}
