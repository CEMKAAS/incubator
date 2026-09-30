package ru.zaroslikov.incubator.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import org.json.JSONException
import org.json.JSONObject
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Сессия аккаунта: кто вошёл и чем подтвердить это серверу. */
data class AccountSession(
    val userId: String,
    val email: String,
    val refreshToken: String,
)

/**
 * Хранилище сессии аккаунта — отдельный файл `account_prefs`, зашифрованный ключом из
 * Android Keystore.
 *
 * **Не база и не `app_prefs`.** Сессия — свойство этой установки, как `device_prefs`: база
 * уезжает в файле экспорта в чужой мессенджер, а refresh-токен в ней был бы ключом от
 * аккаунта в чужих руках. Файл исключён из резервной копии (`backup_rules.xml`) — на
 * другом телефоне ключа Keystore всё равно нет, и восстановленный файл не расшифровался бы.
 *
 * **Шифрует сам, а не через EncryptedSharedPreferences.** Та библиотека устарела и
 * держится на альфа-версии; здесь одно значение, и AES-GCM ключом Keystore — десяток
 * строк, которые видно целиком. Ключ не покидает Keystore, на устройствах с аппаратным
 * хранилищем — не покидает и его.
 *
 * Сессия лежит одним зашифрованным JSON: почта — тоже личные данные, и открытой ей
 * лежать незачем. Файл стирается только тогда, когда он **мёртв** — ключа нет, тег GCM не
 * сошёлся, внутри не JSON: это «вы вышли», а не падение. Любой другой отказ Keystore —
 * а на некоторых прошивках он бывает и мимолётным — сессию не трогает: вернуть `null` на
 * один запуск дешевле, чем разлогинить человека навсегда.
 */
class AccountStore(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): AccountSession? {
        val stored = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val json = JSONObject(decrypt(stored))
            AccountSession(
                userId = json.getString("userId"),
                email = json.getString("email"),
                refreshToken = json.getString("refreshToken"),
            )
        } catch (e: Exception) {
            if (isDead(e)) clear()
            null
        }
    }

    /**
     * `commit`, а не `apply`: токен после ротации старый уже недействителен, и потерянная
     * при гибели процесса запись означала бы выход из аккаунта при следующем запуске.
     */
    fun save(session: AccountSession): Boolean {
        val json = JSONObject()
            .put("userId", session.userId)
            .put("email", session.email)
            .put("refreshToken", session.refreshToken)
        return try {
            prefs.edit().putString(KEY_SESSION, encrypt(json.toString())).commit()
        } catch (e: Exception) {
            false
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).commit()
    }

    /** Отказы, после которых расшифровать эти байты не получится уже никогда. */
    private fun isDead(e: Exception): Boolean =
        e is AEADBadTagException ||
            e is UnrecoverableKeyException ||
            e is KeyPermanentlyInvalidatedException ||
            e is MissingKeyException ||
            e is IllegalArgumentException ||
            e is JSONException

    /** Ключ для чтения: нет его — читать нечем, новый тут не поможет. */
    private fun existingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: throw MissingKeyException()
    }

    private class MissingKeyException : Exception()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** `iv(12) + шифротекст с тегом`, в Base64. IV выдаёт сам Keystore — свой на каждую запись. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }

    companion object {
        /** Имя повторено в `backup_rules.xml` и `data_extraction_rules.xml` — держать в согласии. */
        const val FILE_NAME = "account_prefs"
        private const val KEY_SESSION = "session"
        private const val KEY_ALIAS = "incubator_account_session"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
