package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.AssistantStateRepository
import com.example.service.VoiceAssistantService
import com.example.ui.VoxoraViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
  fun `test assistant state repository and view model synchronization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AssistantStateRepository.setAssistantActive(false)
    val viewModel = VoxoraViewModel()
    viewModel.syncState(context)

    assertFalse(viewModel.uiState.value.isAssistantActive)
    assertEquals("ASSISTANT STOPPED", viewModel.uiState.value.statusText)
    assertEquals("START ASSISTANT", viewModel.uiState.value.buttonText)

    // Simulate service starting
    val serviceController = Robolectric.buildService(VoiceAssistantService::class.java).create()
    val startIntent = Intent().apply { action = VoiceAssistantService.ACTION_START_SERVICE }
    serviceController.get().onStartCommand(startIntent, 0, 1)

    assertTrue(AssistantStateRepository.isAssistantActive.value)
    viewModel.syncState(context)
    assertTrue(viewModel.uiState.value.isAssistantActive)
    assertEquals("ASSISTANT ACTIVE", viewModel.uiState.value.statusText)
    assertEquals("STOP ASSISTANT", viewModel.uiState.value.buttonText)

    // Simulate service stopping
    val stopIntent = Intent().apply { action = VoiceAssistantService.ACTION_STOP_SERVICE }
    serviceController.get().onStartCommand(stopIntent, 0, 2)

    assertFalse(AssistantStateRepository.isAssistantActive.value)
    viewModel.syncState(context)
    assertFalse(viewModel.uiState.value.isAssistantActive)
    assertEquals("ASSISTANT STOPPED", viewModel.uiState.value.statusText)
    assertEquals("START ASSISTANT", viewModel.uiState.value.buttonText)
  }

}




