package ru.zaroslikov.incubator.domain.repository

import ru.zaroslikov.incubator.domain.model.Time

interface WorkRepository {
    fun scheduleReminder(list: MutableList<Time>, name: String)
    fun cancelAllNotifications(name: String)
}
