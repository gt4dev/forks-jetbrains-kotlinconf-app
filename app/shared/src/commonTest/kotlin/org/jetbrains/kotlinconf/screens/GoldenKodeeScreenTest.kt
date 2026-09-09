package org.jetbrains.kotlinconf.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.kotlinconf.LocalNotificationService
import org.jetbrains.kotlinconf.TimeProvider
import org.jetbrains.kotlinconf.di.YearGraph
import org.jetbrains.kotlinconf.flags.Flags
import org.jetbrains.kotlinconf.flags.FlagsManager
import org.jetbrains.kotlinconf.network.ApplicationApi
import org.jetbrains.kotlinconf.storage.ApplicationStorage
import org.jetbrains.kotlinconf.utils.Logger
import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.AwardCategory
import org.jetbrains.kotlinconf.AwardCategoryId
import org.jetbrains.kotlinconf.GoldenKodeeData
import org.jetbrains.kotlinconf.ConferenceService
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.answering.returns
import org.jetbrains.kotlinconf.Nominee
import org.jetbrains.kotlinconf.NomineeId
import org.jetbrains.kotlinconf.ui.theme.KotlinConfLightColors
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GoldenKodeeScreenTest {
    @Test
    fun nomineeClick_reportsCategoryAndNomineeIds() = runComposeUiTest {
        val data = MutableStateFlow<GoldenKodeeData?>(GoldenKodeeData(listOf(category)))
        var clicked: Pair<AwardCategoryId, NomineeId>? = null

        withScreen(data, onNomineeClick = { categoryId, nomineeId ->
            clicked = categoryId to nomineeId
        }) {
            onNodeWithText(category.title).performScrollTo().assertIsDisplayed()
            onNodeWithText(nominee.name).performScrollTo().assertIsDisplayed().performClick()
            runOnIdle { assertEquals(category.id to nominee.id, clicked) }
        }
    }

    @Test
    fun serviceEmissions_updateTheScreenThroughTheRealViewModel() = runComposeUiTest {
        val data = MutableStateFlow<GoldenKodeeData?>(null)

        withScreen(data) {
            onNodeWithText(category.title).assertDoesNotExist()

            runOnIdle { data.value = GoldenKodeeData(listOf(category)) }
            onNodeWithText(category.title).performScrollTo().assertIsDisplayed()
            onNodeWithText(nominee.name).performScrollTo().assertIsDisplayed()

            // A null service value is mapped to empty categories by the real VM.
            runOnIdle { data.value = null }
            onNodeWithText(nominee.name).assertDoesNotExist()
            onNodeWithText(category.title).assertDoesNotExist()
        }
    }

    private fun ComposeUiTest.withScreen(
        data: MutableStateFlow<GoldenKodeeData?>,
        onNomineeClick: (AwardCategoryId, NomineeId) -> Unit = { _, _ -> },
        assertions: ComposeUiTest.() -> Unit,
    ) {
        // Use real constructor dependencies: generated constructor stubs cannot run coroutines.
        val serviceScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher())
        val http = HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) })
        val storage = mock<ApplicationStorage> {
            every { initialize() } returns Unit
            every { userId } returns MutableStateFlow("test-user")
            every { getConfig() } returns flowOf(null)
            every { getFlags() } returns flowOf(Flags())
        }
        val logger = object : Logger {
            override fun log(tag: String, lazyMessage: () -> String) = Unit
        }
        val clock = mock<TimeProvider> {
            every { time } returns MutableStateFlow(LocalDateTime(2026, 5, 21, 12, 0))
        }
        // This separate dispatcher keeps service background work queued during UI tests.
        try {
            val service = object : ConferenceService(
                appClient = ApplicationApi(http, logger),
                applicationStorage = storage,
                timeProvider = clock,
                yearGraphFactory = mock<YearGraph.Factory>(),
                localNotificationService = mock<LocalNotificationService>(),
                flagsManager = FlagsManager(Flags(), storage, serviceScope),
                scope = serviceScope,
                logger = logger,
            ) {
                override val goldenKodeeData get() = data
            }
            withServiceScreen(service, onNomineeClick, assertions)
        } finally {
            serviceScope.cancel()
            http.close()
        }
    }

    private fun ComposeUiTest.withServiceScreen(
        service: ConferenceService,
        onNomineeClick: (AwardCategoryId, NomineeId) -> Unit,
        assertions: ComposeUiTest.() -> Unit,
    ) {
        // Retain the VM during recomposition and cancel its viewModelScope at teardown.
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val factory = object : MetroViewModelFactory() {
            override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel> =
                mapOf(GoldenKodeeViewModel::class to { GoldenKodeeViewModel(service) })
            override val manualAssistedFactoryProviders:
                Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory> =
                emptyMap()
        }

        try {
            setContent {
                CompositionLocalProvider(
                    LocalMetroViewModelFactory provides factory,
                    LocalViewModelStoreOwner provides owner,
                ) {
                    KotlinConfTheme(colors = KotlinConfLightColors) {
                        GoldenKodeeScreen(onNomineeClick = onNomineeClick)
                    }
                }
            }
            assertions()
        } finally {
            runOnIdle { owner.viewModelStore.clear() }
        }
    }

    private companion object {
        val nominee = Nominee(
            id = NomineeId("test-nominee"),
            name = "Test Nominee",
            photoUrl = "", // No network request; use the avatar's local error image.
            bio = "Test nominee biography",
            winner = true,
        )
        val category = AwardCategory(
            id = AwardCategoryId("test-category"),
            title = "Test Award Category",
            nominees = listOf(nominee),
        )
    }
}
