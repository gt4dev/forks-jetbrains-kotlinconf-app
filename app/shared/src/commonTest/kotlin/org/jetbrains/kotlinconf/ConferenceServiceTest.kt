package org.jetbrains.kotlinconf

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.di.YearGraph
import org.jetbrains.kotlinconf.flags.Flags
import org.jetbrains.kotlinconf.flags.FlagsManager
import org.jetbrains.kotlinconf.network.ApplicationApi
import org.jetbrains.kotlinconf.storage.ApplicationStorage
import org.jetbrains.kotlinconf.utils.Logger
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConferenceServiceTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun injectedConstructor_initializesStorageAndStartsBackgroundWork() = runTest {
        val storage = mock<ApplicationStorage> {
            every { initialize() } returns Unit
            every { userId } returns MutableStateFlow("test-user")
            every { getConfig() } returns flowOf(null)
            every { getFlags() } returns flowOf(Flags())
        }
        val logger = object : Logger {
            override fun log(tag: String, lazyMessage: () -> String) = Unit
        }
        var clockStarted = false
        var timeRead = false
        val clock = object : TimeProvider {
            override val time: MutableStateFlow<LocalDateTime>
                get() {
                    timeRead = true
                    return MutableStateFlow(now())
                }
            override fun now() = LocalDateTime(2026, 5, 21, 12, 0)
            override suspend fun run(): Nothing {
                clockStarted = true
                awaitCancellation()
            }
        }
        // The real API gets an in-memory engine; this test cannot use the network.
        val http = HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) })
        try {
            val service = ConferenceService(
                appClient = ApplicationApi(http, logger),
                applicationStorage = storage,
                timeProvider = clock,
                yearGraphFactory = mock<YearGraph.Factory>(),
                localNotificationService = mock<LocalNotificationService>(),
                flagsManager = FlagsManager(Flags(), storage, backgroundScope),
                scope = backgroundScope,
                logger = logger,
            )

            verify { storage.initialize() }
            // No service getter has been read: production flows must start eagerly.
            assertTrue(timeRead)
            runCurrent()
            assertTrue(clockStarted)
            assertNull(service.goldenKodeeData.value)
        } finally {
            http.close()
        }
    }
}
