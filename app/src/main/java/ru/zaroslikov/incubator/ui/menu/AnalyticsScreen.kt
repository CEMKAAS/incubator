package ru.zaroslikov.incubator.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.ads.rememberBannerAdHost
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.design.components.SlidingTab
import ru.zaroslikov.incubator.design.components.SlidingTabSwitcher
import ru.zaroslikov.incubator.ui.incubator.FinanceTab
import ru.zaroslikov.incubator.ui.incubator.StatsTab
import ru.zaroslikov.incubator.ui.incubator.TabScope
import ru.zaroslikov.incubator.ui.incubator.plural
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

object AnalyticsDestination : NavigationDestination {
    override val route = "Analytics"
    override val titleRes = R.string.app_name
}

private enum class AnalyticsTab(val title: String, val icon: Int) {
    Stats("Статистика", R.drawable.ic_chart_design),
    Finance("Финансы", R.drawable.ic_wallet_design),
}

private val analyticsTabs = AnalyticsTab.entries.map { SlidingTab(it.title, it.icon) }

/**
 * «Аналитика» — хозяйство целиком: сколько оно вывело и сколько на нём денег.
 *
 * Шапка отвечает только на вопрос «какого размера хозяйство» — одной строкой под
 * заголовком: сколько устройств и сколько закладок идёт. Итоги — вывод, яйца в работе,
 * баланс — живут на вкладках, где у каждого числа стоит свой знаменатель; в шапке они
 * висели без него и читались как оценка, а не как сведения.
 *
 * Вкладки — те же две, что и у одного инкубатора, только посчитанные по всем закладкам
 * сразу (см. [AnalyticsViewModel]). Своих карточек у экрана нет ни одной, и это
 * осознанно: «средний вывод по хозяйству» и «средний вывод по инкубатору» — один и тот
 * же вопрос с разной областью, и два экрана, отвечающие на него по-разному, рано или
 * поздно ответили бы по-разному и в числах. Вкладка названа «Статистика», а не
 * «Аналитика», чтобы не совпадать с названием самого экрана — и заодно так же, как на
 * экране инкубатора.
 */
@Composable
fun AnalyticsScreen(
    navigateBack: () -> Unit,
    navigateToProfile: () -> Unit,
    viewModel: AnalyticsViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // По владельцу объявления на вкладку, оба — на уровне экрана: страница пейджера
    // уходит из композиции при свайпе, см. BannerAdHost.
    val statsAdHost = rememberBannerAdHost()
    val financeAdHost = rememberBannerAdHost()
    val pagerState = rememberPagerState(pageCount = { AnalyticsTab.entries.size })
    val scope = rememberCoroutineScope()

    MenuScreen(
        title = "Аналитика",
        subtitle = farmSubtitle(uiState),
        navigateBack = navigateBack,
        contentPadding = contentPadding,
        // Прокрутку экран ведёт сам: вкладки — пейджер, страница внутри со своим
        // скроллом, и внешний по той же оси сломал бы и его, и weight(1f) у пейджера.
        scrollable = false,
    ) {
        NameCard(
            name = uiState.user.name,
            onEdit = navigateToProfile,
        )

        SlidingTabSwitcher(
            tabs = analyticsTabs,
            position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
            modifier = Modifier
                .padding(horizontal = MenuScreenPadding)
                .padding(bottom = 12.dp),
        )
        HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            // До первого ответа базы обе вкладки — сплошные нули, и сводка выглядит
            // посчитанной: «0 ₽» баланса и «0 %» вывода это утверждение о хозяйстве,
            // а не отсутствие ответа. Поэтому вместо страницы — колесо.
            if (uiState.loading) {
                LoadingBox()
                return@HorizontalPager
            }
            AnalyticsPage {
                when (AnalyticsTab.entries[page]) {
                    // TabScope.All меняет только подписи: «инкубаторы + яйца» вместо
                    // «инкубатор + яйца» и так далее. Числа считает тот же `:domain`.
                    // Реклама стоит внутри самих вкладок, сразу после первого блока, —
                    // экрану остаётся только дать каждой своего владельца объявления.
                    AnalyticsTab.Stats -> StatsTab(uiState.stats, TabScope.All, statsAdHost)
                    AnalyticsTab.Finance -> {
                        FinanceTab(uiState.finance, TabScope.All, financeAdHost)
                        MissingPricesNote(uiState.incubatorsWithoutPrice)
                    }
                }
            }
        }
    }

}

/**
 * «Цену не указали у 2 инкубаторов» — оговорка под финансами хозяйства.
 *
 * Нужна ровно потому же, почему «Финансы» трижды повторяют оговорку про закладки без
 * цены: инкубатор без цены входит в сумму нулём, окупаемость считается от заниженной
 * цены техники и выходит лучше настоящей. У одного инкубатора эта дыра видна сразу —
 * карточка окупаемости прямо говорит «цена не указана», — а у пяти, где цена есть у
 * четырёх, она бесшумна: число не пустое, просто неправильное. Поэтому строка
 * появляется всегда, когда хоть одно устройство молчит о своей цене, и исчезает, когда
 * все назвались.
 */
@Composable
private fun MissingPricesNote(count: Int) {
    if (count <= 0) return
    Spacer(Modifier.height(12.dp))
    Text(
        // Родительный после «у»: «у 1 инкубатора», «у 2 инкубаторов», «у 5 инкубаторов»,
        // «у 21 инкубатора» — форма единственного числа возвращается на 21, и `plural`
        // это умеет.
        text = "Цену не указали у " +
            plural(count, "инкубатора", "инкубаторов", "инкубаторов") +
            " — окупаемость посчитана по неполной стоимости техники.",
        style = DesignType.Note,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * «3 инкубатора · 2 активные закладки» — размер хозяйства одной строкой под заголовком.
 *
 * Одной строкой, а не парой крупных плиток: это не итоги работы, а масштаб, на фоне
 * которого итоги читаются. Числа же, ради которых на экран заходят — вывод, баланс,
 * отбраковка, — живут на вкладках, где у каждого стоит свой знаменатель.
 *
 * Пустая, пока инкубаторов нет: «0 инкубаторов» под названием раздела — это укор, а не
 * сведения. По той же причине умалчивается и «0 активных закладок»: у хозяйства, где всё
 * доведено до конца, их отсутствие — не новость, и подпись остаётся одними устройствами.
 */
private fun farmSubtitle(uiState: AnalyticsUiState): String? {
    if (uiState.incubatorCount == 0) return null
    val devices = plural(uiState.incubatorCount, "инкубатор", "инкубатора", "инкубаторов")
    if (uiState.activeBatches == 0) return devices
    val batches = plural(
        uiState.activeBatches,
        "активная закладка",
        "активные закладки",
        "активных закладок",
    )
    return "$devices · $batches"
}

/** Страница пейджера: общие отступы и собственная прокрутка — как на экране инкубатора. */
@Composable
private fun AnalyticsPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MenuScreenPadding)
            .padding(top = 20.dp, bottom = 32.dp),
        content = content,
    )
}


/**
 * Карточка с именем — вход в «Профиль».
 *
 * Дублирует заголовок шапки намеренно: в шапке имя — подпись под зелёным, и что оно
 * правится, оттуда не видно. Здесь же стоит строка с действием, а когда профиля нет —
 * приглашение его завести. Своего окна правки у «Аналитики» больше нет: имя — часть
 * профиля, и второе место, где его можно поменять, было бы вторым ответом на вопрос,
 * какое из двух главнее.
 */
@Composable
private fun NameCard(name: String, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MenuScreenPadding)
            .padding(top = 20.dp, bottom = 16.dp)
    ) {
        MenuRow(
            title = name.ifBlank { "Как вас зовут?" },
            description = if (name.isBlank()) {
                "Профиль необязателен — он хранится только на телефоне"
            } else {
                "Владелец хозяйства"
            },
            onClick = onEdit,
        )
    }
}

