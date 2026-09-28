package ru.zaroslikov.incubator.settings

import android.app.backup.BackupAgentHelper
import android.app.backup.FullBackupDataOutput
import ru.zaroslikov.incubator.data.checkpointForBackup

/**
 * Готовит базу к резервной копии Android.
 *
 * Автобэкап копирует файлы приложения как есть, а Room держит базу в режиме WAL: свежие
 * записи лежат в соседнем `-wal`, и он из копии исключён (`res/xml/backup_rules.xml`) —
 * журнал, попавший к другой базе, либо накатит на неё посторонние страницы, либо не даст
 * ей открыться. Без контрольной точки в облако уходила бы база на момент последнего
 * слияния журнала: без последних замеров, а то и без последней закладки. Человек узнал бы
 * об этом, только когда стал бы из этой копии восстанавливаться.
 *
 * Ровно то же самое и по той же причине делает «Сохранить копию» (`exportDatabase` в
 * `:data`); разница в цене неудачи, поэтому [checkpointForBackup] не бросает исключение.
 *
 * Ключ-значение бэкапа у приложения нет: [BackupAgentHelper] без единого хелпера
 * закрывает `onBackup`/`onRestore` пустыми, а всё, что копируется, копируется правилами.
 */
class IncubatorBackupAgent : BackupAgentHelper() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        checkpointForBackup(applicationContext)
        super.onFullBackup(data)
    }
}
