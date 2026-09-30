package ru.zaroslikov.incubator.ui.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.account.AccountSubscription
import ru.zaroslikov.incubator.account.PremiumPlan
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.design.theme.displayFontFamily
import ru.zaroslikov.incubator.ui.incubator.plural
import ru.zaroslikov.incubator.ui.mvi.CollectEffects

/*
 * Палитра окна Premium — своя и одна для обеих тем, как белая карточка QR-кода: это
 * витрина, а не часть рабочего экрана. Тёплый почти-чёрный фон и золото говорят
 * «премиум» без слов; в светлой теме тёмная шторка ещё и отделяет предложение от кремовых
 * экранов вокруг. Зелёный акцент приложения здесь нарочно не используется — он значит
 * «идёт, в работе», а не «особенное».
 */
private val Ink = Color(0xFF15120E)
private val InkRaised = Color(0xFF221D17)
private val InkLine = Color(0xFF3A3128)
private val Gold = Color(0xFFE6C27A)
private val GoldDeep = Color(0xFFB8893B)
private val GoldLight = Color(0xFFF8E7BD)
private val TextBright = Color(0xFFF6F0E6)
private val TextMuted = Color(0xFFB4A994)
private val Danger = Color(0xFFF08B74)

private val GoldBrush = Brush.linearGradient(listOf(GoldLight, Gold, GoldDeep))

/**
 * «Premium» — предложение, оплата и подтверждение в одной шторке.
 *
 * Сначала — что даёт Premium (без рекламы, резервные копии на сервере), затем тарифы с
 * сервера и золотая «Оформить Premium». Оплата — на странице платёжной системы в
 * браузере; шторка тем временем ждёт ответа сервера и, когда он приходит, сменяется
 * поздравлением. Возврат из браузера ([LifecycleResumeEffect]) спрашивает о платеже
 * сразу, не дожидаясь очередного опроса.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PremiumSheet(
    viewModel: PremiumViewModel,
    subscription: AccountSubscription?,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { send(PremiumIntent.Open) }
    LifecycleResumeEffect(Unit) {
        send(PremiumIntent.Resumed)
        onPauseOrDispose { }
    }

    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // Браузера нет: шторка остаётся на ожидании с кнопкой «Открыть страницу оплаты»,
            // и платёж, если его всё же оплатят с другого устройства, подтвердится опросом.
        }
    }
    CollectEffects(viewModel) { effect ->
        when (effect) {
            is PremiumEffect.OpenPayment -> openUrl(effect.url)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = null,
        // Тёмная шторка под строкой состояния прятала бы её тёмные значки; ниже неё
        // значки стоят на затемнённом экране профиля и видны.
        modifier = Modifier.statusBarsPadding(),
    ) {
        Box {
            // Тёплое свечение сверху — единственная «декорация», и та из самого фона.
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(GoldDeep.copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.22f),
                        radius = size.width * 0.75f,
                    )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PremiumTopBar(onClose = onDismiss)
                AnimatedContent(
                    targetState = state.stage,
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                    label = "premiumStage",
                ) { stage ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when (stage) {
                            PremiumStage.Offer -> OfferContent(state, send)
                            PremiumStage.Waiting -> WaitingContent(
                                paymentUrl = state.paymentUrl,
                                onReopen = { state.paymentUrl?.let(openUrl) },
                                onBack = { send(PremiumIntent.BackToOffer) },
                            )
                            PremiumStage.Done -> DoneContent(subscription, onClose = onDismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumTopBar(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(width = 40.dp, height = 5.dp)
                .clip(CircleShape)
                .background(InkLine)
        )
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(InkRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun OfferContent(state: PremiumState, send: (PremiumIntent) -> Unit) {
    CrownEmblem()
    Spacer(Modifier.height(20.dp))
    GoldText("Инкубатор Premium", style = PremiumTitle)
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Больше спокойствия для вашего хозяйства",
        style = DesignType.Body,
        color = TextMuted,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))

    Benefit(
        icon = R.drawable.ic_premium_no_ads_design,
        title = "Без рекламы",
        text = "Ни баннеров в списках, ни рекламы при запуске — только ваши инкубаторы и закладки.",
    )
    Spacer(Modifier.height(10.dp))
    Benefit(
        icon = R.drawable.ic_premium_cloud_design,
        title = "Резервные копии на сервере",
        text = "Сохраняйте все данные в облако и восстанавливайте их на новом телефоне — " +
            "ничего не потеряется, даже если с этим что-то случится.",
    )

    Spacer(Modifier.height(24.dp))
    PlansBlock(state, send)

    state.error?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = DesignType.Micro, color = Danger, textAlign = TextAlign.Center)
    }

    Spacer(Modifier.height(20.dp))
    GoldButton(
        text = "Оформить Premium",
        enabled = state.canSubscribe,
        busy = state.busy,
        onClick = { send(PremiumIntent.Subscribe) },
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Оплата — на защищённой странице платёжной системы. Premium начинает " +
            "действовать сразу после оплаты.",
        style = DesignType.Note,
        color = TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** Корона в золотом круге, с мягким «дыханием» свечения вокруг. */
@Composable
private fun CrownEmblem(size: Int = 88) {
    val glow = rememberInfiniteTransition(label = "crownGlow")
    val pulse by glow.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "crownPulse",
    )
    Box(Modifier.padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size((size + 36).dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    alpha = 1.15f - pulse * 0.5f
                }
                .background(
                    Brush.radialGradient(listOf(Gold.copy(alpha = 0.35f), Color.Transparent)),
                    CircleShape,
                )
        )
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF3B2F1F), InkRaised)))
                .border(1.5.dp, GoldBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_premium_crown_design),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size((size * 0.5f).dp)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(GoldBrush, blendMode = BlendMode.SrcAtop)
                    },
            )
        }
        Sparkle(Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 10.dp), 12)
        Sparkle(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 14.dp), 8)
    }
}

/** Четырёхлучевая искра — пара штук у короны, не больше. */
@Composable
private fun Sparkle(modifier: Modifier, size: Int) {
    Canvas(modifier.size(size.dp)) {
        val w = this.size.width
        val c = w / 2f
        val path = Path().apply {
            moveTo(c, 0f)
            quadraticTo(c, c, w, c)
            quadraticTo(c, c, c, w)
            quadraticTo(c, c, 0f, c)
            quadraticTo(c, c, c, 0f)
            close()
        }
        drawPath(path, GoldLight)
    }
}

@Composable
private fun Benefit(icon: Int, title: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(InkRaised)
            .border(0.8.dp, InkLine, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.12f))
                .border(1.dp, Gold.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Gold, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = DesignType.ListItemTitle, color = TextBright)
            Spacer(Modifier.height(4.dp))
            Text(text, style = DesignType.Caption, color = TextMuted)
        }
    }
}

@Composable
private fun PlansBlock(state: PremiumState, send: (PremiumIntent) -> Unit) {
    when {
        state.plansLoading -> Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        state.plansError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.plansError, style = DesignType.Caption, color = TextMuted, textAlign = TextAlign.Center)
            TextButton(onClick = { send(PremiumIntent.RetryPlans) }) {
                Text("Попробовать ещё раз", style = DesignType.ButtonLabel, color = Gold)
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val longest = state.plans.maxOfOrNull { it.durationDays }
            state.plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    selected = plan.id == state.selectedPlanId,
                    best = state.plans.size > 1 && plan.durationDays == longest,
                    onClick = { send(PremiumIntent.SelectPlan(plan.id)) },
                )
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PremiumPlan, selected: Boolean, best: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Gold.copy(alpha = 0.10f) else InkRaised)
            .then(
                if (selected) Modifier.border(1.5.dp, GoldBrush, shape)
                else Modifier.border(0.8.dp, InkLine, shape)
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.title.ifBlank { periodLabel(plan.durationDays) },
                    style = DesignType.ListItemTitle,
                    color = TextBright,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (best) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Выгоднее",
                        style = DesignType.Micro.copy(fontWeight = FontWeight.SemiBold),
                        color = Ink,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GoldBrush)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            monthlyHint(plan)?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = DesignType.Caption, color = TextMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = plan.priceDisplay,
            style = TextStyle(
                fontFamily = displayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 24.sp,
            ),
            color = if (selected) GoldLight else TextBright,
        )
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (selected) Gold else TextMuted.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(GoldBrush)
            )
        }
    }
}

/**
 * «≈ 81 ₽ в месяц» — под тарифом длиннее двух месяцев: цену года человек мысленно делит
 * на двенадцать, и лучше сделать это за него. Только для рублей: знак другой валюты
 * клиент не знает, а у месячного тарифа подпись повторяла бы название.
 */
internal fun monthlyHint(plan: PremiumPlan): String? {
    if (plan.durationDays < 60 || plan.amountKopecks <= 0 || plan.currency != "RUB") return null
    val perMonth = plan.amountKopecks * 30 / plan.durationDays / 100
    return "≈ ${formatCount(perMonth.toInt())} ₽ в месяц"
}

/** «30 дней», «на год» — срок тарифа словами, для подписи под названием. */
internal fun periodLabel(days: Int): String = when {
    days <= 0 -> ""
    days % 365 == 0 -> if (days == 365) "на год" else "на ${plural(days / 365, "год", "года", "лет")}"
    days in 28..31 -> "на месяц"
    days % 30 == 0 && days < 365 -> "на ${plural(days / 30, "месяц", "месяца", "месяцев")}"
    else -> "на ${plural(days, "день", "дня", "дней")}"
}

/**
 * Главная кнопка — золотая, с бликом, который проходит по ней раз в пару секунд. Блик
 * рисуется в `drawWithContent` от бесконечной анимации: композиция при этом не меняется,
 * перерисовывается только слой.
 */
@Composable
private fun GoldButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shine = rememberInfiniteTransition(label = "goldShine")
    val sweep by shine.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "goldSweep",
    )
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .graphicsLayer { alpha = if (enabled || busy) 1f else 0.45f }
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(GoldDeep, Gold, GoldLight, Gold, GoldDeep)))
            .drawWithContent {
                drawContent()
                if (enabled) {
                    val x = size.width * sweep
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                            start = Offset(x - size.width * 0.18f, 0f),
                            end = Offset(x + size.width * 0.18f, size.height),
                        )
                    )
                }
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Ink, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_premium_crown_design),
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(text, style = DesignType.ButtonLabel.copy(fontWeight = FontWeight.SemiBold), color = Ink)
            }
        }
    }
}

@Composable
private fun WaitingContent(paymentUrl: String?, onReopen: () -> Unit, onBack: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = Gold,
            trackColor = InkLine,
            strokeWidth = 3.dp,
            modifier = Modifier.size(88.dp),
        )
        Icon(
            painterResource(R.drawable.ic_premium_crown_design),
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(36.dp),
        )
    }
    Spacer(Modifier.height(24.dp))
    GoldText("Ждём подтверждения оплаты", style = PremiumTitle.copy(fontSize = 24.sp, lineHeight = 30.sp))
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Завершите оплату на открывшейся странице и вернитесь в приложение — " +
            "Premium включится сам, как только платёжная система подтвердит оплату.",
        style = DesignType.Body,
        color = TextMuted,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    if (paymentUrl != null) {
        GoldButton(text = "Открыть страницу оплаты", enabled = true, busy = false, onClick = onReopen)
        Spacer(Modifier.height(8.dp))
    }
    TextButton(onClick = onBack) {
        Text("Вернуться к тарифам", style = DesignType.ButtonLabel, color = TextMuted)
    }
}

@Composable
private fun DoneContent(subscription: AccountSubscription?, onClose: () -> Unit) {
    CrownEmblem()
    Spacer(Modifier.height(20.dp))
    GoldText("Premium подключён!", style = PremiumTitle)
    Spacer(Modifier.height(10.dp))
    val until = subscription?.let(::premiumUntil)
    Text(
        text = "Спасибо, что поддерживаете «Инкубатор»! Реклама больше не показывается" +
            (until?.let { " — Premium действует до $it." } ?: "."),
        style = DesignType.Body,
        color = TextMuted,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    GoldButton(text = "Отлично", enabled = true, busy = false, onClick = onClose)
}

/**
 * «Купить Premium» на экране профиля — тёмная плашка с золотом, той же палитры, что и
 * шторка, которую она открывает: нажимающий заранее видит, куда попадёт.
 */
@Composable
internal fun BuyPremiumButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2C2419), Ink)))
            .border(1.2.dp, GoldBrush, shape)
            .clickable(role = Role.Button, onClickLabel = "Купить Premium", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.14f))
                .border(1.dp, Gold.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_premium_crown_design),
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            GoldText("Купить Premium", style = DesignType.ListItemTitle, center = false)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Без рекламы и с резервными копиями на сервере",
                style = DesignType.Caption,
                color = TextMuted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("›", style = DesignType.SectionTitle, color = Gold)
    }
}

/** Значок «Premium» рядом с именем — только у действующей подписки. */
@Composable
internal fun PremiumBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GoldBrush)
            .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_premium_crown_design),
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Premium",
            style = DesignType.Micro.copy(fontWeight = FontWeight.SemiBold),
            color = Ink,
            maxLines = 1,
        )
    }
}

/** Заголовок, залитый золотым градиентом. */
@Composable
private fun GoldText(text: String, style: TextStyle, center: Boolean = true) {
    Text(
        text = text,
        style = style.copy(brush = GoldBrush),
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

private val PremiumTitle = TextStyle(
    fontFamily = displayFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
)
