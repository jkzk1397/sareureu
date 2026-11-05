package com.capstone.pause.core.intervention.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 서비스에는 LifecycleOwner가 없어서 오버레이 `ComposeView`마다 직접 붙인다(ONE_SHOT 8절 1번).
 * 창을 붙일 때 [attach] → RESUMED, 뗄 때 [destroy] → DESTROYED.
 */
internal class OverlayLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    /** 뷰 트리에 붙이고 RESUMED로. 창을 WindowManager에 더하기 전에 부른다. */
    fun attach(view: View) {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        registry.currentState = Lifecycle.State.RESUMED
    }

    /** 창을 뗀 뒤 DESTROYED로. */
    fun destroy() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
        store.clear()
    }
}
