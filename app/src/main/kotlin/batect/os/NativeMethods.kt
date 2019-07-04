/*
   Copyright 2017-2019 Charles Korn.

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

package batect.os

interface NativeMethods {
    fun getConsoleDimensions(): Dimensions

    fun getUserId(): PossiblyUnsupportedValue<Int>
    fun getGroupId(): PossiblyUnsupportedValue<Int>

    fun getUserName(): String
    fun getGroupName(): PossiblyUnsupportedValue<String>
}

sealed class PossiblyUnsupportedValue<T> {
    abstract fun getValueOrDefault(default: T): T
    abstract fun getValueOrThrow(): T

    data class Supported<T>(val value: T) : PossiblyUnsupportedValue<T>() {
        override fun getValueOrDefault(default: T): T = value
        override fun getValueOrThrow(): T = value
    }

    data class Unsupported<T>(val explanation: String) : PossiblyUnsupportedValue<T>() {
        override fun getValueOrDefault(default: T): T = default
        override fun getValueOrThrow(): T = throw UnsupportedOperationException("This value is not supported: $explanation")
    }
}

data class Dimensions(val height: Int, val width: Int)

abstract class NativeMethodException(val method: String, val errorName: String, val errorDescription: String) :
    RuntimeException("Invoking native method $method failed with error $errorName ($errorDescription).")

class NoConsoleException() : RuntimeException("STDOUT is not connected to a console.")