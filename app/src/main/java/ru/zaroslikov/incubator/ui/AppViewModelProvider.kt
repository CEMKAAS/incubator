package ru.zaroslikov.incubator.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.zaroslikov.incubator.InventoryApplication
import ru.zaroslikov.incubator.ui.add.AddIncubatorViewModel
import ru.zaroslikov.incubator.ui.incubator.IncubatorEditDayViewModel
import ru.zaroslikov.incubator.ui.incubator.IncubatorOvoscopViewModel
import ru.zaroslikov.incubator.ui.incubator.IncubatorProjectEditViewModel
import ru.zaroslikov.incubator.ui.incubator.IncubatorViewModel
import ru.zaroslikov.incubator.ui.start.StartScreenViewModel


object AppViewModelProvider {
    val Factory = viewModelFactory {

        initializer {
            StartScreenViewModel(
                inventoryApplication().container.itemsRepository
            )
        }

        initializer {
            AddIncubatorViewModel(
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.workRepository
            )
        }

        initializer {
            IncubatorProjectEditViewModel(
                this.createSavedStateHandle(),
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.workRepository
            )
        }

        initializer {
            IncubatorViewModel(
                this.createSavedStateHandle(),
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.workRepository
            )
        }

        initializer {
            IncubatorOvoscopViewModel(
                this.createSavedStateHandle()
            )
        }

        initializer {
            IncubatorEditDayViewModel(
                this.createSavedStateHandle(),
                inventoryApplication().container.itemsRepository
            )
        }
    }
}

fun CreationExtras.inventoryApplication(): InventoryApplication =
    (this[AndroidViewModelFactory.APPLICATION_KEY] as InventoryApplication)
