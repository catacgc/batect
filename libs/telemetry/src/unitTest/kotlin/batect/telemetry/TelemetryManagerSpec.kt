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

package batect.telemetry

import batect.logging.Logger
import batect.logging.Severity
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.logging.InMemoryLogSink
import batect.testutils.logging.hasMessage
import batect.testutils.logging.withAdditionalData
import batect.testutils.logging.withException
import batect.testutils.logging.withLogMessage
import batect.testutils.logging.withSeverity
import batect.testutils.osIndependentPath
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import java.nio.file.Files
import java.time.Duration
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TelemetryManagerSpec : Spek({
    describe("a telemetry manager") {
        val telemetryConsent by createForEachTest { mock<TelemetryConsent>() }
        val telemetryUploadQueue by createForEachTest { mock<TelemetryUploadQueue>() }
        val telemetryConfigurationStore by createForEachTest { mock<TelemetryConfigurationStore>() }
        val ciEnvironmentDetector by createForEachTest { mock<CIEnvironmentDetector>() }
        val abacusClient by createForEachTest { mock<AbacusClient>() }
        val logSink by createForEachTest { InMemoryLogSink() }
        val logger by createForEachTest { Logger("Test logger", logSink) }
        val telemetryManager by createForEachTest { TelemetryManager(telemetryConsent, telemetryUploadQueue, telemetryConfigurationStore, ciEnvironmentDetector, abacusClient, logger) }

        val session by createForEachTest { mock<TelemetrySession>() }
        val sessionBuilder by createForEachTest {
            mock<TelemetrySessionBuilder> {
                on { build(telemetryConfigurationStore) } doReturn session
            }
        }

        given("telemetry is disabled") {
            beforeEachTest {
                whenever(telemetryConsent.telemetryAllowed).thenReturn(false)

                telemetryManager.finishSession(sessionBuilder)
            }

            it("does not save the session to the upload queue") {
                verify(telemetryUploadQueue, never()).add(any())
            }
        }

        given("telemetry is enabled") {
            beforeEachTest {
                whenever(telemetryConsent.telemetryAllowed).thenReturn(true)
            }

            given("the application is running on a CI system") {
                val sessionPath by createForEachTest { osIndependentPath("session-123.json") }
                val sessionBytes = byteArrayOf(0x01, 0x02, 0x03)

                beforeEachTest {
                    whenever(ciEnvironmentDetector.detect()).doReturn(CIDetectionResult(true, "My CI Tool"))
                    whenever(telemetryUploadQueue.add(session)).doReturn(sessionPath)

                    Files.write(sessionPath, sessionBytes)
                }

                given("uploading the session succeeds") {
                    beforeEachTest {
                        telemetryManager.finishSession(sessionBuilder)
                    }

                    it("saves the session to the upload queue") {
                        verify(telemetryUploadQueue).add(session)
                    }

                    it("uploads the session with a 2 second timeout") {
                        verify(abacusClient).upload(sessionBytes, Duration.ofSeconds(2))
                    }

                    it("removes the session from the upload queue") {
                        verify(telemetryUploadQueue).pop(sessionPath)
                    }
                }

                given("uploading the session fails") {
                    val exception by createForEachTest { RuntimeException("Something went wrong.") }

                    beforeEachTest {
                        whenever(abacusClient.upload(any(), anyOrNull())).doThrow(exception)

                        telemetryManager.finishSession(sessionBuilder)
                    }

                    it("saves the session to the upload queue") {
                        verify(telemetryUploadQueue).add(session)
                    }

                    it("attempts to upload the session") {
                        verify(abacusClient).upload(any(), anyOrNull())
                    }

                    it("does not remove the session from the upload queue") {
                        verify(telemetryUploadQueue, never()).pop(any())
                    }

                    it("logs a warning that uploading the session failed") {
                        assertThat(logSink, hasMessage(
                            withLogMessage("Immediate upload of telemetry session failed, will be queued to upload in the background next time.")
                                and withSeverity(Severity.Error)
                                and withAdditionalData("sessionPath", sessionPath.toString())
                                and withException(exception)
                        ))
                    }
                }
            }

            given("the application is not running on a CI system") {
                beforeEachTest {
                    whenever(ciEnvironmentDetector.detect()).doReturn(CIDetectionResult(false, null))

                    telemetryManager.finishSession(sessionBuilder)
                }

                it("saves the session to the upload queue") {
                    verify(telemetryUploadQueue).add(session)
                }

                it("does not upload the session") {
                    verifyZeroInteractions(abacusClient)
                }

                it("does not remove the session from the upload queue") {
                    verify(telemetryUploadQueue, never()).pop(any())
                }
            }
        }
    }
})
