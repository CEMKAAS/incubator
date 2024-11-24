package ru.zaroslikov.incubator.data

import android.content.Context
import ru.zaroslikov.incubator.data.work.WorkManagerRepository
import ru.zaroslikov.incubator.data.work.WorkRepository

interface AppContainer {
    val itemsRepository: ItemsRepository
    val workRepository : WorkRepository
}

class AppDataContainer(private val context: Context) : AppContainer {
    override val itemsRepository: ItemsRepository by lazy {
        OfflineItemsRepository(InventoryDatabase.getDatabase(context).itemDao())
    }
    override val workRepository = WorkManagerRepository(context)
}

