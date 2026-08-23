package ru.zaroslikov.incubator.data

import android.content.Context
import ru.zaroslikov.incubator.data.local.InventoryDatabase
import ru.zaroslikov.incubator.data.repository.OfflineItemsRepository
import ru.zaroslikov.incubator.domain.repository.ItemsRepository

/**
 * Единственная точка входа в слой данных. Room, БД и DAO остаются деталями модуля :data
 * и намеренно не видны из :app — поэтому room-runtime подключён как implementation.
 */
fun provideItemsRepository(context: Context): ItemsRepository =
    OfflineItemsRepository(InventoryDatabase.getDatabase(context).itemDao())
