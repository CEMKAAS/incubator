package ru.zaroslikov.incubator.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.zaroslikov.incubator.InventoryApplication
import ru.zaroslikov.incubator.ui.batch.AddBatchViewModel
import ru.zaroslikov.incubator.ui.incubator.AddIncubatorViewModel
import ru.zaroslikov.incubator.ui.incubator.IncubatorMeasurementViewModel
import ru.zaroslikov.incubator.ui.incubator.IncubatorViewModel
import ru.zaroslikov.incubator.ui.batch.BatchDetailViewModel
import ru.zaroslikov.incubator.ui.batch.CandlingViewModel
import ru.zaroslikov.incubator.ui.batch.FinishGroupViewModel
import ru.zaroslikov.incubator.ui.menu.AnalyticsViewModel
import ru.zaroslikov.incubator.ui.menu.SettingsViewModel
import ru.zaroslikov.incubator.ui.qr.IncubatorQrViewModel
import ru.zaroslikov.incubator.ui.qr.ScanQrViewModel
import ru.zaroslikov.incubator.ui.species.CustomSpeciesViewModel
import ru.zaroslikov.incubator.ui.start.StartScreenViewModel


object AppViewModelProvider {
    val Factory = viewModelFactory {

        initializer {
            BatchDetailViewModel(
                inventoryApplication().container.appSettings,
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.workRepository,
                inventoryApplication().container.airingTimer,
            )
        }

        initializer {
            FinishGroupViewModel(
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.workRepository,
            )
        }

        initializer {
            IncubatorMeasurementViewModel(
                inventoryApplication().container.appSettings,
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.airingTimer,
            )
        }

        initializer {
            StartScreenViewModel(
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.workRepository
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
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.workRepository,
                inventoryApplication().container.scheduleTransfer
            )
        }

        initializer {
            AddBatchViewModel(
                inventoryApplication().container.appSettings,
                inventoryApplication().container.itemsRepository,
                inventoryApplication().container.workRepository,
                inventoryApplication().container.scheduleTransfer
            )
        }

        initializer {
            CandlingViewModel(
                inventoryApplication().container.itemsRepository
            )
        }

        initializer {
            AnalyticsViewModel(
                inventoryApplication().container.itemsRepository
            )
        }

        initializer {
            CustomSpeciesViewModel(
                inventoryApplication().container.appSettings,
                inventoryApplication().container.itemsRepository
            )
        }

        initializer {
            SettingsViewModel(
                inventoryApplication().container.appSettings,
                inventoryApplication().container.transferController,
                inventoryApplication().container.itemsRepository
            )
        }

        initializer {
            IncubatorQrViewModel(
                inventoryApplication(),
                inventoryApplication().container.itemsRepository
            )
        }

        initializer {
            ScanQrViewModel(
                inventoryApplication().container.itemsRepository
            )
        }
    }
}

fun CreationExtras.inventoryApplication(): InventoryApplication =
    (this[AndroidViewModelFactory.APPLICATION_KEY] as InventoryApplication)
