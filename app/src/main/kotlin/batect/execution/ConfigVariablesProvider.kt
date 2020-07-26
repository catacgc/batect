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

import batect.cli.CommandLineOptionsParser
import batect.config.Configuration
import batect.config.ProjectPaths
import batect.config.io.ConfigurationException
import batect.config.io.ConfigurationFileException
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlInput
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class ConfigVariablesProvider(
    private val commandLineOverrides: Map<String, String>,
    private val sourceFile: Path?,
    private val projectPaths: ProjectPaths
) {
    fun build(config: Configuration): Map<String, String?> {
        val defaults = defaultValues(config)
        val fromFile = loadFileValues(config)

        validateCommandLineOverrides(config)

        val builtIns = mapOf(
            "batect.project_directory" to projectPaths.projectRootDirectory.toString()
        )

        return defaults + fromFile + commandLineOverrides + builtIns
    }

    private fun defaultValues(config: Configuration): Map<String, String?> =
        config.configVariables.associate { it.name to it.defaultValue }

    private fun loadFileValues(config: Configuration): Map<String, String> {
        if (sourceFile == null) {
            return emptyMap()
        }

        val absolutePath = sourceFile.toAbsolutePath()
        val configFileContent = Files.readAllBytes(absolutePath).toString(Charset.defaultCharset())

        try {
            val nameSerializer = ConfigVariableNameSerializer(config, sourceFile)

            return Yaml().parse(MapSerializer(nameSerializer, String.serializer()), configFileContent)
        } catch (e: YamlException) {
            throw ConfigurationFileException(e.message, absolutePath.toString(), e.line, e.column, e)
        }
    }

    private fun validateCommandLineOverrides(config: Configuration) {
        commandLineOverrides.keys.forEach { name ->
            if (!config.configVariables.containsKey(name)) {
                throw ConfigurationException("The config variable '$name' set with --${CommandLineOptionsParser.configVariableOptionName} has not been defined.")
            }
        }
    }

    private class ConfigVariableNameSerializer(private val config: Configuration, private val sourceFile: Path) : KSerializer<String> {
        override val descriptor: SerialDescriptor = String.serializer().descriptor

        override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

        override fun deserialize(decoder: Decoder): String {
            val name = decoder.decodeString()

            if (!config.configVariables.containsKey(name)) {
                val yaml = decoder as YamlInput
                val location = yaml.getCurrentLocation()

                throw ConfigurationFileException("The config variable '$name' has not been defined.", sourceFile.toString(), location.line, location.column)
            }

            return name
        }
    }
}
