package ru.zaroslikov.incubator.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.zaroslikov.incubator.design.theme.DesignPalette

/**
 * Задержка перед тем, как показать колесо.
 *
 * База лежит на этом же телефоне, и обычный ответ приходит быстрее, чем глаз успевает
 * его заметить. Колесо, мигнувшее на два кадра, читается как подёргивание экрана, а не
 * как загрузка, — поэтому первые полторы десятых секунды место просто пустует, и только
 * если ответа всё ещё нет, в нём появляется колесо. Место при этом занято с самого
 * начала: [LoadingBox] растягивается сразу, так что содержимое потом не прыгает.
 */
private const val SPINNER_DELAY_MILLIS = 150L

/** Сколько места отводится колесу на месте формы в шторке — примерно её собственный рост. */
val FormLoaderHeight = 240.dp

/**
 * «Идёт загрузка» — то, что стоит на месте содержимого, пока база не ответила.
 *
 * Нужно ровно потому, что пустое состояние и непрочитанное состояние выглядели
 * одинаково: и у списка без инкубаторов, и у списка, который ещё не приехал, `cards`
 * пуст, и экран честно писал «Добро пожаловать!» человеку с пятью инкубаторами. Разводит
 * их не эта функция, а флаг загрузки в состоянии экрана; здесь — только его вид.
 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingSpinner()
    }
}

/** Само колесо — с той самой задержкой, чтобы не мигать на быстрых ответах. */
@Composable
fun LoadingSpinner(size: Dp = 36.dp) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPINNER_DELAY_MILLIS)
        visible = true
    }
    if (!visible) return
    CircularProgressIndicator(
        color = DesignPalette.Accent,
        trackColor = DesignPalette.ProgressTrack,
        strokeWidth = 3.dp,
        modifier = Modifier.size(size),
    )
}

/**
 * Число в плитке-показателе, пока его ещё не посчитали.
 *
 * Ноль здесь — такой же обманщик, как пустой список: «0 · Активных закладок» над
 * работающим инкубатором это утверждение, а не отсутствие ответа. Прочерк ничего не
 * утверждает и лишнего места не просит.
 */
fun statValue(loading: Boolean, value: Int): String =
    if (loading) "—" else formatCount(value)
