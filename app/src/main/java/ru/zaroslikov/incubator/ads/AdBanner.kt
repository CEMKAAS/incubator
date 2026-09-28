package ru.zaroslikov.incubator.ads

import android.content.Context
import android.view.ViewGroup
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.AdTheme
import com.yandex.mobile.ads.common.ImpressionData
import ru.zaroslikov.incubator.design.theme.DarkDesignColors
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.LocalDesignPalette

/** Скругление карточки — то же, что у карточек инкубатора, закладки и вкладок. */
private val CardCorner = 22.dp

/** Рамка карточки вокруг объявления: белая поверхность, видная по краям. */
private val CardInset = 8.dp

/** Потолок высоты объявления в dp; какую именно взять, SDK решает сам. */
private const val MaxBannerHeightDp = 120

/** Под что заказано объявление: под ширину и тему; сменилось любое — заказ новый. */
internal data class BannerKey(val widthDp: Int, val theme: AdTheme)

/**
 * Владелец объявления на один экран — то, что переживает уход карточки из композиции.
 *
 * Карточка стоит в конце `LazyColumn` или на странице пейджера, а и то и другое
 * выбрасывает содержимое, ушедшее с экрана, и собирает заново по возвращении. Если бы
 * вид жил в самой карточке, каждая прокрутка мимо и каждый свайп вкладки заказывали бы
 * новое объявление — по шесть запросов на два показа, что портит заполнение и похоже
 * на накрутку. Поэтому вид держит этот объект, запомненный на уровне экрана
 * ([rememberBannerAdHost]), а карточка только просит его у владельца: вернулась на
 * экран — получила тот же, уже загруженный.
 *
 * Отсюда правило: **на экране одна карточка рекламы, не по одной на вкладку** — один
 * `View` не может стоять в двух родителях, а во время свайпа две страницы пейджера
 * скомпонованы одновременно.
 *
 * Уничтожается вместе с композицией экрана — при уходе с него и при пересоздании
 * активности, — так что контекст активности, на котором создан вид, не переживёт её.
 */
@Stable
class BannerAdHost internal constructor() {

    private var view: BannerAdView? = null
    private var viewKey: BannerKey? = null

    /**
     * Под какой ключ объявление загружено; `null` — ни под какой. Сравнивается с ключом
     * текущей карточки, поэтому смена ширины или темы сама читается как «не загружено»,
     * и сбрасывать флаг из `factory` — то есть из композиции — не приходится.
     */
    internal var loadedFor: BannerKey? by mutableStateOf(null)
        private set

    /** Вид под этот ключ: прежний, если ключ не менялся, иначе новый с новым заказом. */
    internal fun viewFor(context: Context, key: BannerKey): BannerAdView {
        view?.let { existing ->
            if (viewKey == key) {
                // Compose уже снял его с прежнего места; страховка на случай, если нет.
                (existing.parent as? ViewGroup)?.removeView(existing)
                return existing
            }
            existing.setBannerAdEventListener(null)
            existing.destroy()
        }
        val created = BannerAdView(context).apply {
            setAdSize(BannerAdSize.inline(context, key.widthDp, MaxBannerHeightDp))
            setBannerAdEventListener(object : BannerAdEventListener {
                override fun onAdLoaded() {
                    loadedFor = key
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    if (loadedFor == key) loadedFor = null
                }

                override fun onAdClicked() {}
                override fun onImpression(impressionData: ImpressionData?) {}
            })
            loadAd(
                AdRequest.Builder(MobileAdsSdk.BANNER_UNIT_ID)
                    .setPreferredTheme(key.theme)
                    .build()
            )
        }
        view = created
        viewKey = key
        return created
    }

    internal fun destroy() {
        view?.setBannerAdEventListener(null)
        view?.destroy()
        view = null
        viewKey = null
        loadedFor = null
    }
}

/**
 * Владелец объявления, живущий столько же, сколько экран, который его запомнил.
 * Звать на уровне экрана, а не внутри списка или страницы: см. [BannerAdHost].
 */
@Composable
fun rememberBannerAdHost(): BannerAdHost {
    val host = remember { BannerAdHost() }
    DisposableEffect(host) {
        onDispose { host.destroy() }
    }
    return host
}

/** Через сколько карточек списка повторяется объявление после первого. */
private const val AdEveryItems = 3

/**
 * Сколько рекламных мест максимум бывает в одном списке.
 *
 * Ограничение не про вкус, а про память: каждое объявление — это живой `BannerAdView`
 * со своим `WebView` внутри, и у хозяйства с полусотней закладок «по одному на каждые
 * три» означало бы полтора десятка таких на экране. Пять мест покрывают пятнадцать
 * карточек — заметно больше, чем бывает у настоящего инкубатора, — а дальше список идёт
 * без рекламы.
 */
private const val MaxAdSlots = 5

/**
 * Номер рекламного места после карточки [index]; `null` — здесь рекламы нет.
 *
 * Правило: **первое объявление сразу после первой карточки, дальше через каждые три.**
 * То есть после карточек 1, 4, 7, 10 — в нумерации с нуля это `index % 3 == 0`.
 *
 * Первая карточка стоит особняком не ради красоты счёта: список открывается на ней, и
 * объявление сразу под ней — единственное, которое человек с любым списком увидит
 * наверняка. Дальше идёт ровный шаг в три карточки, чтобы реклама попадалась при
 * прокрутке, но не мельтешила.
 *
 * Длину списка функция не спрашивает: первая карточка есть у всякого непустого списка,
 * так что отдельного правила для коротких не нужно. Пустому списку рекламное место
 * достаётся отдельной веткой у вызывающего — карточки, за которой встать, там нет.
 */
internal fun adSlotAfter(index: Int): Int? {
    if (index % AdEveryItems != 0) return null
    val slot = index / AdEveryItems
    return if (slot < MaxAdSlots) slot else null
}

/**
 * Набор владельцев объявлений на один список — по одному на рекламное место.
 *
 * Одним владельцем тут не обойтись: у списка их несколько сразу, а один `View` не может
 * стоять в двух родителях. Заводятся они по требованию, и это важно: место, до которого
 * не долистали, не создаёт ни вида, ни запроса в сеть.
 *
 * Живёт на уровне экрана — как и одиночный [BannerAdHost] и ровно по той же причине:
 * `LazyColumn` выбрасывает уехавшую с экрана карточку, и владелец внутри неё заказывал бы
 * объявление заново при каждой прокрутке мимо.
 */
@Stable
class BannerAdHosts internal constructor() {

    private val hosts = mutableMapOf<Int, BannerAdHost>()

    /** Владелец объявления для места [slot]; заводится при первом обращении. */
    fun host(slot: Int): BannerAdHost = hosts.getOrPut(slot) { BannerAdHost() }

    internal fun destroy() {
        hosts.values.forEach { it.destroy() }
        hosts.clear()
    }
}

/** Набор владельцев, живущий столько же, сколько экран, который его запомнил. */
@Composable
fun rememberBannerAdHosts(): BannerAdHosts {
    val hosts = remember { BannerAdHosts() }
    DisposableEffect(hosts) {
        onDispose { hosts.destroy() }
    }
    return hosts
}

/**
 * Объявление после карточки [index] — если ему тут место.
 *
 * Ставится сразу за карточкой в теле списка; какие места считаются рекламными, решает
 * [adSlotAfter]. Ничего не рисует там, где места нет, поэтому вызывать можно после
 * каждой карточки без всяких условий у себя.
 */
@Composable
fun AdBannerAfter(
    index: Int,
    hosts: BannerAdHosts,
    modifier: Modifier = Modifier,
) {
    val slot = adSlotAfter(index) ?: return
    AdBanner(hosts.host(slot), modifier)
}

/**
 * Рекламный баннер как карточка списка.
 *
 * Это не липкая полоса внизу экрана, какой реклама была в прежней версии, а ещё одна
 * белая карточка со скруглением 22 dp и той же рамкой, что у соседей, — стоит в конце
 * прокрутки там, где у экрана есть список: под инкубаторами, под закладками, под
 * статистикой, под настройками. Объявление внутри — «inline», ширина по карточке,
 * высота до [MaxBannerHeightDp]: SDK подбирает её сам под то, что прислал, и карточка
 * её обнимает. Тема объявления — тема экрана: тёмному приложению заказывается тёмное
 * объявление, чтобы карточка рекламы не осталась единственным белым пятном. Что тема
 * тёмная, читается из [LocalDesignPalette] — того же источника, из которого красится
 * сама карточка.
 *
 * **Пока объявление не загрузилось, карточки нет — ни рамки, ни высоты, ни отступа
 * из [modifier].** Пустая белая карточка в списке читалась бы как сломанная, а «нет
 * рекламы» — обычное состояние: без сети, без заполнения, на эмуляторе. Вид при этом
 * всё равно в композиции — на нулевой высоте, — чтобы ему было куда загрузиться; по
 * загрузке карточка раскрывается `animateContentSize`, а не появляется рывком.
 *
 * Ширину объявлению нужно знать до загрузки, отсюда `BoxWithConstraints` и `key` по
 * ней и по теме: сменилось любое — вид создаётся заново под новый заказ. SDK поднимается
 * по первому баннеру, если реклама при запуске его ещё не подняла
 * ([MobileAdsSdk.start]), и до его готовности здесь ничего нет.
 *
 * @param host владелец объявления этого экрана — [rememberBannerAdHost] на уровне
 *   экрана, чтобы прокрутка мимо и свайп вкладки не заказывали объявление заново.
 */
@Composable
fun AdBanner(host: BannerAdHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sdkReady by MobileAdsSdk.ready.collectAsState()
    // Побочное действие — не в композиции: она может повториться сколько угодно раз.
    LaunchedEffect(Unit) { MobileAdsSdk.start(context) }
    if (!sdkReady) return

    val shape = RoundedCornerShape(CardCorner)
    val dark = LocalDesignPalette.current === DarkDesignColors
    val adTheme = if (dark) AdTheme.DARK else AdTheme.LIGHT

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bannerKey = BannerKey((maxWidth - CardInset * 2).value.toInt(), adTheme)
        val loaded = host.loadedFor == bannerKey
        Box(
            modifier = Modifier
                .then(if (loaded) modifier else Modifier)
                .fillMaxWidth()
                .animateContentSize()
                .then(
                    if (loaded) Modifier
                        .clip(shape)
                        .background(DesignPalette.Surface)
                        .border(0.8.dp, DesignPalette.CardBorder, shape)
                        .padding(CardInset)
                    else Modifier
                        .height(0.dp)
                        .clipToBounds()
                )
        ) {
            key(bannerKey) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CardCorner - CardInset)),
                    factory = { ctx -> host.viewFor(ctx, bannerKey) },
                )
            }
        }
    }
}
