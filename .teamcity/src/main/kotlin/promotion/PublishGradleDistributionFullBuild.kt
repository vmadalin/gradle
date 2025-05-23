/*
 * Copyright 2022 the original author or authors.
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

package promotion

abstract class PublishGradleDistributionFullBuild(
    // The branch to be promoted
    promotedBranch: String,
    prepTask: String? = null,
    promoteTask: String,
    triggerName: String,
    gitUserName: String = "bot-teamcity",
    gitUserEmail: String = "bot-teamcity@gradle.com",
    extraParameters: String = "",
) : BasePublishGradleDistribution(promotedBranch, prepTask, triggerName, gitUserName, gitUserEmail, extraParameters) {
    init {
        steps {
            if (prepTask != null) {
                buildStep(extraParameters, gitUserName, gitUserEmail, triggerName, prepTask, "uploadAll")
                buildStep(extraParameters, gitUserName, gitUserEmail, triggerName, prepTask, promoteTask)
            } else {
                buildStep(
                    listOf(extraParameters, "-PpromotedBranch=$promotedBranch").joinToString(separator = " "),
                    gitUserName,
                    gitUserEmail,
                    triggerName,
                    promoteTask,
                    "",
                )
            }
        }
    }
}
