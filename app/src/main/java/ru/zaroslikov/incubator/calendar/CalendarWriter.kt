package ru.zaroslikov.incubator.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Календарь телефона, в который можно писать. */
data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val primary: Boolean,
)

/**
 * Запись важных дат закладки в системный календарь (`CalendarContract`).
 *
 * **Напрямую, с разрешением, а не интентом «создать событие».** Интент открывает
 * календарь на одно событие, а дат у закладки четыре-пять: пять раз подряд нажать
 * «Сохранить» в чужом приложении — это не «добавить даты», это работа. Интент остался
 * запасным путём ([insertIntent]) — на случай отказа в разрешении или телефона без
 * календаря, куда можно писать: нажатая кнопка должна что-то сделать.
 *
 * События — **на весь день**, с напоминанием накануне в 18:00: овоскопирование или
 * перекладку делают в течение дня, а не к минуте, и вечер перед ним — время, когда
 * о нём полезно узнать. У событий на весь день время начала — полночь UTC, так
 * требует провайдер; напоминание он сам пересчитывает в местный день.
 */
object CalendarWriter {

    val PERMISSIONS: Array<String> = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    /** Минут до начала события на весь день: полночь минус шесть часов — 18:00 накануне. */
    private const val REMIND_EVENING_BEFORE = 6 * 60

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun hasPermission(context: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Видимые календари, куда можно добавлять события, — основной первым. Пустой список —
     * обычный ответ: на телефоне без аккаунта календарей может не быть вовсе.
     * Блокирующий вызов, звать не с главного потока.
     */
    fun calendars(context: Context): List<DeviceCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
            "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR} AND " +
            // Календарь аккаунта с выключенной синхронизацией бывает видимым, но события
            // из него никуда не уходят и в самом календаре могут не показаться.
            "${CalendarContract.Calendars.SYNC_EVENTS} = 1"
        val result = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result += DeviceCalendar(
                    id = cursor.getLong(0),
                    name = cursor.getString(1).orEmpty(),
                    account = cursor.getString(2).orEmpty(),
                    primary = !cursor.isNull(3) && cursor.getInt(3) == 1,
                )
            }
        }
        return result.sortedWith(compareByDescending<DeviceCalendar> { it.primary }.thenBy { it.name })
    }

    /**
     * Записывает [dates] в календарь [calendarId] одним `applyBatch` — все события с
     * напоминаниями или ни одного: половина дат в календаре хуже, чем ни одной, потому
     * что выглядит как все. Возвращает число записанных событий.
     * Блокирующий вызов, звать не с главного потока.
     */
    fun insert(
        context: Context,
        calendarId: Long,
        offer: CalendarOffer,
        dates: List<CalendarDate>,
    ): Int {
        val ops = ArrayList<ContentProviderOperation>()
        dates.forEach { date ->
            val eventIndex = ops.size
            val start = utcMidnight(date.date)
            ops += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                .withValue(CalendarContract.Events.TITLE, date.eventTitle(offer))
                .withValue(CalendarContract.Events.DESCRIPTION, date.eventDescription(offer))
                .withValue(CalendarContract.Events.DTSTART, start)
                .withValue(CalendarContract.Events.DTEND, start + DAY_MILLIS)
                .withValue(CalendarContract.Events.ALL_DAY, 1)
                .withValue(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                .withValue(CalendarContract.Events.HAS_ALARM, 1)
                // «Свободен», а не «занят» по умолчанию: в рабочем календаре пять дней,
                // целиком помеченных занятыми, увидели бы коллеги при поиске времени.
                .withValue(
                    CalendarContract.Events.AVAILABILITY,
                    CalendarContract.Events.AVAILABILITY_FREE,
                )
                .build()
            ops += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventIndex)
                .withValue(CalendarContract.Reminders.MINUTES, REMIND_EVENING_BEFORE)
                .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                .build()
        }
        val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
        return results.indices.count { it % 2 == 0 && results[it].uri != null }
    }

    /**
     * Запасной путь: календарь открывается на одном событии, заполненном заранее, и
     * сохраняет его человек. Разрешения не нужно. Время — местная полночь: так интент
     * понимают календари для события на весь день.
     */
    fun insertIntent(offer: CalendarOffer, date: CalendarDate): Intent {
        val begin = date.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, date.eventTitle(offer))
            .putExtra(CalendarContract.Events.DESCRIPTION, date.eventDescription(offer))
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + DAY_MILLIS)
    }

    private fun utcMidnight(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
