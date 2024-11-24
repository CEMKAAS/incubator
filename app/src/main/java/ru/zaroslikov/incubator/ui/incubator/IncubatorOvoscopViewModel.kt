package ru.zaroslikov.incubator.ui.incubator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import ru.zaroslikov.incubator.ui.incubator.IncubatorOvoscopDestination

class IncubatorOvoscopViewModel (
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val dayVM: Int = checkNotNull(savedStateHandle[IncubatorOvoscopDestination.itemIdArg])
    val typeBirds: String =
        checkNotNull(savedStateHandle[IncubatorOvoscopDestination.itemIdArgTwo])
}