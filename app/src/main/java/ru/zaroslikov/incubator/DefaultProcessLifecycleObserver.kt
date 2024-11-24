package ru.zaroslikov.incubator

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class DefaultProcessLifecycleObserver(
    private val onProcessCaneForeground: () -> Unit
): DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner){
        onProcessCaneForeground()
    }
}