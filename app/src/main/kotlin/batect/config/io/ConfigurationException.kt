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

package batect.config.io

data class ConfigurationException(
    override val message: String,
    val lineNumber: Int?,
    val column: Int?,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    constructor(message: String) : this(message, null, null, null)

    override fun toString(): String = when {
        lineNumber != null && column != null -> "Error on line $lineNumber, column $column: $message"
        lineNumber != null -> "Error on line $lineNumber: $message"
        else -> message
    }
}
