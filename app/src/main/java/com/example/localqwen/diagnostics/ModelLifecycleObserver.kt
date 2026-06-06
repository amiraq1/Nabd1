package com.example.localqwen.diagnostics

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.localqwen.viewmodel.ModelViewModel

/**
 * Monitors the Android Lifecycle to manage native model memory.
 * Unloads the model when the app goes to the background and reloads it (warm start)
 * when the user returns, preventing OOM crashes while the app is inactive.
 */
class ModelLifecycleObserver(private val modelViewModel: ModelViewModel) : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                modelViewModel.onAppBackgrounded()
            }
            Lifecycle.Event.ON_START -> {
                modelViewModel.onAppForegrounded()
            }
            else -> {}
        }
    }
}
