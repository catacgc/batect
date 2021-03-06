/*
   Copyright 2017-2020 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.execution

import batect.cli.CommandLineOptions
import batect.config.Configuration
import batect.config.Task
import batect.logging.Logger
import batect.utils.asHumanReadableList

class TaskExecutionOrderResolver(
    private val commandLineOptions: CommandLineOptions,
    private val suggester: TaskSuggester,
    private val logger: Logger
) {
    fun resolveExecutionOrder(config: Configuration, taskName: String): List<Task> {
        val task = config.tasks[taskName]

        if (task == null) {
            throw TaskDoesNotExistException("The task '$taskName' does not exist." + formatTaskSuggestions(config, taskName) + " (Run './batect --list-tasks' for a list of all tasks in this project, or './batect --help' for help.)")
        }

        val executionOrder = if (commandLineOptions.skipPrerequisites) {
            listOf(task)
        } else {
            resolvePrerequisitesForTask(config, task, listOf(task), emptyList())
        }

        logger.info {
            message("Resolved task execution order.")
            data("executionOrder", executionOrder.map { it.name })
            data("skipPrerequisites", commandLineOptions.skipPrerequisites)
        }

        return executionOrder
    }

    private fun resolvePrerequisitesForTask(config: Configuration, task: Task, path: List<Task>, executionOrderSoFar: List<Task>): List<Task> {
        val prerequisites = task.prerequisiteTasks
            .map { prerequisiteTaskName -> resolvePrerequisiteForTask(config, task, prerequisiteTaskName, path) }

        var executionOrder = executionOrderSoFar

        prerequisites.forEach { prerequisite ->
            if (prerequisite !in executionOrder) {
                executionOrder = resolvePrerequisitesForTask(config, prerequisite, path + prerequisite, executionOrder)
            }
        }

        return executionOrder + task
    }

    private fun resolvePrerequisiteForTask(config: Configuration, parentTask: Task, prerequisiteTaskName: String, path: List<Task>): Task {
        val prerequisite = config.tasks[prerequisiteTaskName]

        if (prerequisite == null) {
            val message = "The task '$prerequisiteTaskName' given as a prerequisite of '${parentTask.name}' does not exist." + formatTaskSuggestions(config, prerequisiteTaskName)
            throw PrerequisiteTaskDoesNotExistException(message)
        }

        if (path.contains(prerequisite)) {
            val description = cycleDescription(path + prerequisite)
            throw DependencyCycleException("There is a dependency cycle between tasks: $description.")
        }

        return prerequisite
    }

    private fun cycleDescription(path: List<Task>): String {
        val taskNames = path.map { "'${it.name}'" }
        val firstPart = "task ${taskNames[0]} has ${taskNames[1]} as a prerequisite"

        val remainingNames = taskNames.drop(2)
        val remainingPart = remainingNames.map { ", which has $it as a prerequisite" }.joinToString("")

        return firstPart + remainingPart
    }

    private fun formatTaskSuggestions(config: Configuration, originalTaskName: String): String {
        val suggestions = suggester
            .suggestCorrections(config, originalTaskName)
            .map { "'$it'" }

        if (suggestions.isEmpty()) {
            return ""
        }

        return " Did you mean ${suggestions.asHumanReadableList("or")}?"
    }
}

sealed class TaskExecutionOrderResolutionException(override val message: String) : RuntimeException(message) {
    override fun toString(): String = message
}

class TaskDoesNotExistException(message: String) : TaskExecutionOrderResolutionException(message)
class PrerequisiteTaskDoesNotExistException(message: String) : TaskExecutionOrderResolutionException(message)
class DependencyCycleException(message: String) : TaskExecutionOrderResolutionException(message)
