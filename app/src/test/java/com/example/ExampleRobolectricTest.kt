package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.VoxoraViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Voxora", appName)
  }

  @Test
  fun `test voxora view model initial and toggled states`() {
    val viewModel = VoxoraViewModel()
    assertFalse(viewModel.uiState.value.isAssistantActive)
    assertEquals("ASSISTANT STOPPED", viewModel.uiState.value.statusText)
    assertEquals("START ASSISTANT", viewModel.uiState.value.buttonText)

    viewModel.toggleAssistantState()
    assertTrue(viewModel.uiState.value.isAssistantActive)
    assertEquals("ASSISTANT ACTIVE", viewModel.uiState.value.statusText)
    assertEquals("STOP ASSISTANT", viewModel.uiState.value.buttonText)
  }
}

