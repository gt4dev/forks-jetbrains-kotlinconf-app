package org.jetbrains.kotlinconf.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GoldenKodeeScreenTest {
    @Test
    fun parameterlessScreen_hasExpectedSize() = runComposeUiTest {
        // Arrange: explicitly exercises the parameterless preview/POC overload.
        // The overload with onNomineeClick requires a Metro ViewModel provider.
        setContent {
            GoldenKodeeScreen()
        }

        // Assert: the current POC is a 10.dp square with no text or click actions.
        onRoot()
            .assertWidthIsEqualTo(10.dp)
            .assertHeightIsEqualTo(10.dp)

        // TODO: For the full screen, supply deterministic categories through a
        // test ViewModel provider or extract a state-driven content composable.
        // Then assert category/nominee text, winner labels, and callback IDs.
    }
}
