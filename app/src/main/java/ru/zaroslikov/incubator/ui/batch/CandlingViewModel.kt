package ru.zaroslikov.incubator.ui.batch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import ru.zaroslikov.incubator.ui.batch.CandlingDestination

class CandlingViewModel (
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val dayVM: Int = checkNotNull(savedStateHandle[CandlingDestination.itemIdArg])
    val typeBirds: String =
        checkNotNull(savedStateHandle[CandlingDestination.itemIdArgTwo])
}