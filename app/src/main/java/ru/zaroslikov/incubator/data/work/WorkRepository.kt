package ru.zaroslikov.incubator.data.work


import ru.zaroslikov.incubator.data.ferma.Time

interface WorkRepository {
    fun scheduleReminder(list: MutableList<Time>, name: String)
    fun cancelAllNotifications(name: String)
}