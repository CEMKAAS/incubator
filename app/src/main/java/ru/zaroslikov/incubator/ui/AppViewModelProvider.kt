package ru.zaroslikov.incubator.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.zaroslikov.incubator.InventoryApplication
import ru.zaroslikov.incubator.ui.batch.AddBatchViewModel
import ru.zaroslikov.incubator.ui.incubator.AddIncubatorViewModel
import ru.zaroslikov.incubator.ui.incubator.IncubatorViewModel
import ru.zaroslikov.incubator.ui.batch.BatchDayViewModel
import ru.zaroslikov.incubator.ui.batch.BatchDetailViewModel
import ru.zaroslikov.incubator.ui.batch.CandlingViewModel
import ru.zaroslikov.incubator.ui.batch.BatchViewModel
import ru.zaroslikov.incubator.ui.start.StartScreenViewModel


object AppViewModelProvider {
    val Factory = viewModelFactory {

        initializer {
            BatchDetailViewModel(
                inventoryApplication().container.itemsRepository
            )
        }

        initializer {
            StartScreenViewModel(
                inventoryApplication().container.itemsRepository
            )
        }

        initializer {
            AddIncubatorViewModel(
                inventoryApplication().container.itemsRepository
            )
        }

        initializer {
            IncubatorViewModel(
                this.createSavedStateHandle(),
                inventoryApplication().container.itemsRepository
            )
        }

        initializer {
            AddBatchViewModel(
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.workRepository
            )
        }

        initializer {
            BatchViewModel(
                this.createSavedStateHandle(),
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.workRepository
            )
        }

        initializer {
            CandlingViewModel(
                this.createSavedStateHandle()
            )
        }

        initializer {
            BatchDayViewModel(
                this.createSavedStateHandle(),
                inventoryApplication().container.itemsRepository
            )
        }
    }
}

fun CreationExtras.inventoryApplication(): InventoryApplication =
    (this[AndroidViewModelFactory.APPLICATION_KEY] as InventoryApplication)
