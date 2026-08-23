package ru.zaroslikov.incubator.di

import android.content.Context
import ru.zaroslikov.incubator.data.provideItemsRepository
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.repository.WorkRepository
import ru.zaroslikov.incubator.work.WorkManagerRepository

interface AppContainer {
    val itemsRepository: ItemsRepository
    val workRepository : WorkRepository
}

class AppDataContainer(private val context: Context) : AppContainer {
    override val itemsRepository: ItemsRepository by lazy {
        provideItemsRepository(context)
    }
    override val workRepository = WorkManagerRepository(context)
}
