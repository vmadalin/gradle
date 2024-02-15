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

package org.gradle.integtests.tooling.fixture

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.executer.TaskExecuter
import org.gradle.test.fixtures.file.TestFile

class ToolingApiTaskExecuter implements TaskExecuter {

    private GradleExecuter gradleExecuter
    private TestFile settingsFile

    ToolingApiTaskExecuter(GradleExecuter gradleExecuter, TestFile settingsFile) {
        this.gradleExecuter = gradleExecuter
        this.settingsFile = settingsFile
    }

    @Override
    def configure() {
        settingsFile.touch()
    }

    @Override
    ExecutionResult executeTasks(boolean requireIsolatedUserHome, List<String> arguments, String... tasks) {
        def stderr = new TestOutputStream()
        def stdout = new TestOutputStream()
        def toolingApi = new ToolingApi(gradleExecuter.distribution, gradleExecuter.testDirectoryProvider, stdout, stderr)
        if (requireIsolatedUserHome) {
            toolingApi.requireIsolatedUserHome()
        }

        try {
            toolingApi.withConnection { connection ->
                connection.newBuild().forTasks(tasks).addArguments(arguments).run()
            }
            return OutputScrapingExecutionResult.from(stdout.toString(), stderr.toString())
        } catch (Exception exception) {
            return OutputScrapingExecutionFailure.from(stdout.toString(), exception.cause.message.toString())
        }
    }
}
