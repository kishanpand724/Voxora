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

    // Simulate wake word state updates
    AssistantStateRepository.setExplicitlyStopped(context, false)
    AssistantStateRepository.setAssistantActive(true)
    AssistantStateRepository.updateListeningState(com.example.speech.SpeechListeningState.WAITING_FOR_WAKE_WORD)
    viewModel.syncState(context)

    assertTrue(viewModel.uiState.value.isAssistantActive)
    assertEquals("WAITING FOR 'NOVA'", viewModel.uiState.value.statusText)
    assertEquals("Say 'Nova' to activate...", viewModel.uiState.value.infoMessage)

    // Simulate "Nova" wake word detected -> listening for command
    AssistantStateRepository.updateListeningState(com.example.speech.SpeechListeningState.LISTENING_FOR_COMMAND)
    viewModel.syncState(context)

    assertEquals("LISTENING FOR COMMAND", viewModel.uiState.value.statusText)
    assertEquals("Listening for your command...", viewModel.uiState.value.infoMessage)

    // Simulate command received -> processing
    AssistantStateRepository.updateListeningState(com.example.speech.SpeechListeningState.PROCESSING)
    AssistantStateRepository.updateRecognizedText("Open YouTube")
    viewModel.syncState(context)

    assertEquals("PROCESSING COMMAND", viewModel.uiState.value.statusText)
    assertEquals("Open YouTube", viewModel.uiState.value.lastRecognizedText)

    // Simulate service stopping
    AssistantStateRepository.setAssistantActive(false)
    viewModel.syncState(context)
    assertFalse(viewModel.uiState.value.isAssistantActive)
    assertEquals("ASSISTANT STOPPED", viewModel.uiState.value.statusText)
    assertEquals("START ASSISTANT", viewModel.uiState.value.buttonText)


  }

  @Test
  fun `test service task removal and explicit stop state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    // Start service explicitly
    VoiceAssistantService.startService(context)
    assertFalse(AssistantStateRepository.isExplicitlyStopped(context))

    val serviceController = Robolectric.buildService(VoiceAssistantService::class.java).create()
    serviceController.get().onTaskRemoved(Intent())

    // When task is removed, if not explicitly stopped, service remains configured as active
    assertFalse(AssistantStateRepository.isExplicitlyStopped(context))

    // Stop service explicitly
    VoiceAssistantService.stopService(context)
    assertTrue(AssistantStateRepository.isExplicitlyStopped(context))
  }
}





