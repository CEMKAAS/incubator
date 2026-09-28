package ru.zaroslikov.incubator.transfer

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифр файла расписания: AES-256-GCM поверх JSON из [ScheduleFileCodec].
 *
 * Файл лежит у человека в «Загрузках», уходит в мессенджер, приходит от кого-то ещё —
 * и в каждом из этих мест он должен быть закрытым: не открываться текстовым редактором
 * и не приниматься приложением, если по дороге в нём поменяли хоть байт. Второе даёт
 * GCM сам — это шифр с проверкой подлинности, и подделанный или обрезанный файл
 * отказывается расшифровываться, а не превращается в расписание с мусором в клетках.
 *
 * **Ключ один на все экземпляры приложения, и это не недосмотр, а условие задачи.**
 * Файл должен открываться на чужом телефоне, где нет ни учётной записи, ни пароля, ни
 * обмена ключами: единственное, что есть у обоих, — это само приложение. Ключ из
 * Android Keystore не подошёл бы — он не покидает устройства, и файл открывался бы
 * только там, где сделан. Честная граница такого шифра: он закрывает файл от чужих
 * программ и от случайного взгляда и не даёт его изменить незаметно; от того, кто
 * разобрал APK и вынул ключ, он не защищает. Внутри файла нет ничего, что стоило бы
 * такой защиты, — режим инкубации.
 *
 * Формат: [MAGIC] (4 байта, чтобы чужой файл отклонялся по первым байтам, не доходя до
 * шифра), версия контейнера (1 байт), случайный nonce (12 байт — свой у каждого файла,
 * иначе два файла с одним ключом раскрывали бы друг друга), и дальше шифртекст с тегом
 * GCM. Заголовок идёт в AAD: подмена версии или магии ломает тег.
 */
object ScheduleFileCipher {

    private val MAGIC = "INCS".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** 256 бит, записанные шестнадцатеричной строкой, чтобы не держать в коде массив из 32 чисел. */
    private const val KEY_HEX =
        "6b1f0c7e93a4d2585fe7c30b8a19d64e21f5b7c0d8a3946e1c5b2f70e9d4a836"

    private val key by lazy { SecretKeySpec(KEY_HEX.hexToByteArray(), "AES") }
    private val random = SecureRandom()

    fun seal(plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
        val header = MAGIC + byteArrayOf(VERSION) + nonce
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    /** @throws ScheduleFileException если это не файл расписания или он изменён. */
    fun open(sealed: ByteArray): ByteArray {
        val headerSize = MAGIC.size + 1 + NONCE_SIZE
        if (sealed.size < headerSize + TAG_BITS / 8 ||
            !sealed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        ) {
            throw ScheduleFileException("Это не файл расписания приложения.")
        }
        val version = sealed[MAGIC.size]
        if (version > VERSION) {
            throw ScheduleFileException(
                "Файл сохранён более новой версией приложения. Обновите приложение."
            )
        }
        val header = sealed.copyOfRange(0, headerSize)
        val nonce = sealed.copyOfRange(MAGIC.size + 1, headerSize)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return try {
            cipher.doFinal(sealed, headerSize, sealed.size - headerSize)
        } catch (e: AEADBadTagException) {
            throw ScheduleFileException("Файл повреждён или изменён — открыть его нельзя.")
        }
    }

    private fun String.hexToByteArray(): ByteArray =
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
