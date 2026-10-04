package ru.zaroslikov.incubator.ui.qr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.design.R as DesignR
import ru.zaroslikov.incubator.design.components.StatusBarAppearance
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.qr.QrFrameAnalyzer
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.mvi.CollectEffects
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ScanQrDestination : NavigationDestination {
    override val route = "ScanQr"
}

private val ScreenPadding = 20.dp

/** Доля меньшей стороны экрана под окно видоискателя. */
private const val WindowFraction = 0.68f

/** Полупрозрачный чёрный поверх камеры — под шапкой, подписями и вокруг окна. */
private val Scrim = Color.Black.copy(alpha = 0.55f)

/** Кружок под значком на камере: тот же цвет, что и затемнение, только гуще. */
private val ControlSurface = Color.Black.copy(alpha = 0.45f)

/**
 * Экран сканера QR-кода — камера во весь экран и окно видоискателя посередине. Экран чёрный, поверх
 * камеры затемнение с вырезом, строка с названием сверху и подсказка снизу; статус-бар чёрный со
 * светлыми значками.
 *
 * Разрешение на камеру спрашивается при входе. После отказа — объяснение и две кнопки, «спросить
 * снова» и «открыть настройки»: после второго «нет» система диалог не показывает, и без второй
 * кнопки экран был бы тупиком.
 *
 * Распознавание — в [QrFrameAnalyzer], ответ — в [ScanQrViewModel]; текст с потока анализатора
 * переносится на главный через `rememberCoroutineScope`. Без вспышки кнопка фонарика остаётся, но выключена.
 */
@Composable
fun ScanQrScreen(
    navigateBack: () -> Unit,
    /** Инкубатор найден в базе — экран отдаёт его идентификатор и больше ничего не делает. */
    onFound: (Long) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ScanQrViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    StatusBarAppearance(color = Color.Black, lightIcons = true)

    CollectEffects(viewModel) { effect ->
        when (effect) {
            is ScanQrEffect.Open -> onFound(effect.incubatorId)
        }
    }

    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    // Отказ помнится на время экрана (переживает поворот): по нему на месте камеры
    // встаёт объяснение, а не повторный системный вопрос при каждой перерисовке.
    var denied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) {
        if (!granted && !denied) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var torchOn by rememberSaveable { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (granted) {
            CameraPreview(
                torchOn = torchOn,
                onTorchAvailable = { hasTorch = it },
                onDecoded = { text ->
                    scope.launch { viewModel.onIntent(ScanQrIntent.Decoded(text)) }
                },
                modifier = Modifier.fillMaxSize(),
            )
            ViewfinderOverlay(modifier = Modifier.fillMaxSize())
        } else if (denied) {
            PermissionDenied(
                onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { openAppSettings(context) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = ScreenPadding),
            )
        }

        // Шапка — только название: закрывается экран кнопкой внизу, там же, где фонарик,
        // — оба под большим пальцем руки, в которой лежит телефон. Возврат в углу сверху
        // был бы вторым способом сделать то же самое.
        Text(
            text = "Сканировать QR-код",
            style = DesignType.SheetTitle,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = ScreenPadding, vertical = 16.dp),
        )

        // Низ: сообщение о неподходящем коде, подсказка и два круглых управления —
        // фонарик и «Закрыть», как в любой камере.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = contentPadding.calculateBottomPadding())
                .padding(horizontal = ScreenPadding, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Текст держится, пока плашка гаснет: без этого она сжималась бы в пустую
            // точку раньше, чем исчезнет. Последнее сообщение помнится отдельно от
            // состояния, которое к тому моменту уже пустое.
            var lastMessage by remember { mutableStateOf<ScanMessage?>(null) }
            LaunchedEffect(state.message) {
                state.message?.let { lastMessage = it }
            }
            AnimatedVisibility(
                visible = state.message != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MessagePill(text = lastMessage?.text.orEmpty())
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (granted) {
                Text(
                    text = "Наведите камеру на QR-код инкубатора — откроются его «Замеры за сегодня»",
                    style = DesignType.Body,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Фонарик стоит всегда, а не только когда вспышка есть: кнопка, которая
                // появляется на одних телефонах и отсутствует на других, читается как
                // поломка на вторых. Без вспышки она погашена — и подпись говорит почему.
                val torchAvailable = granted && hasTorch
                CameraControl(
                    icon = R.drawable.ic_flash_design,
                    label = if (!torchAvailable) "Нет вспышки" else if (torchOn) "Фонарик вкл." else "Фонарик",
                    contentDescription = if (torchOn) "Выключить фонарик" else "Включить фонарик",
                    enabled = torchAvailable,
                    active = torchOn,
                    onClick = { torchOn = !torchOn },
                )
                CameraControl(
                    icon = DesignR.drawable.ic_close_design,
                    label = "Закрыть",
                    contentDescription = "Закрыть сканер",
                    enabled = true,
                    active = false,
                    onClick = navigateBack,
                )
            }
        }
    }
}

/**
 * Превью камеры с анализатором кадров. `PreviewView` в `AndroidView` — у CameraX нет
 * Compose-версии. Камера привязана к жизненному циклу (`bindToLifecycle`), `DisposableEffect`
 * отвязывает её при уходе с экрана.
 *
 * Разбор кадров — на своём однопоточном исполнителе, который закрывается с экраном.
 * `KEEP_ONLY_LATEST`: отставший кадр выбрасывается. Разрешение анализа — 1280×720 или ближайшее:
 * на 640×480 наклейка в 3 см читалась только вплотную.
 */
@Composable
private fun CameraPreview(
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onDecoded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val decoded by rememberUpdatedState(onDecoded)
    val torchAvailable by rememberUpdatedState(onTorchAvailable)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        // Экран ушёл раньше, чем провайдер ответил, — быстрый «назад» на первом открытии,
        // пока CameraX поднимается: слушатель тогда ничего не привязывает, иначе камера
        // открылась бы ради экрана, которого уже нет, на исполнителе, который уже закрыт.
        var disposed = false
        future.addListener(
            {
                if (disposed) return@addListener
                // `get()` бросает, когда камера сломана или занята при инициализации, и это
                // тоже «камеры нет», а не повод падать: превью останется чёрным, подсказка
                // и кнопка «Закрыть» внизу остаются.
                val cameraProvider = try {
                    future.get()
                } catch (e: Exception) {
                    return@addListener
                }
                provider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    android.util.Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                )
                            )
                            .build()
                    )
                    .build()
                    .also { it.setAnalyzer(executor, QrFrameAnalyzer { text -> decoded(text) }) }
                try {
                    cameraProvider.unbindAll()
                    val bound = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    camera = bound
                    torchAvailable(bound.cameraInfo.hasFlashUnit())
                } catch (e: Exception) {
                    // Камеры нет или она занята — превью останется чёрным, подсказка
                    // внизу остаётся. Падать здесь не за что.
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            provider?.unbindAll()
            camera = null
            executor.shutdown()
        }
    }

    // Фонарик — по состоянию кнопки, и заново при каждом возврате на экран: уход в фон
    // закрывает камеру, и Camera2 сбрасывает фонарик в «выключен», а `Camera` тот же и
    // ключи эффекта не меняются — без повтора кнопка горела бы зелёным над погасшим
    // фонарём, и включить его снова можно было бы только двумя нажатиями.
    var resumeCount by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(camera, torchOn, resumeCount) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Затемнение с вырезом под окно видоискателя и рамкой вокруг него.
 *
 * Вырез — `BlendMode.Clear` по уже нарисованному затемнению, что требует отдельного
 * слоя (`CompositingStrategy.Offscreen`): без него Clear стирал бы и камеру под собой.
 */
@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val side = size.minDimension * WindowFraction
        val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        val corner = CornerRadius(24.dp.toPx())
        drawRect(Scrim)
        drawRoundRect(
            color = Color.Transparent,
            topLeft = topLeft,
            size = Size(side, side),
            cornerRadius = corner,
            blendMode = BlendMode.Clear,
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = topLeft,
            size = Size(side, side),
            cornerRadius = corner,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/**
 * Круглая кнопка на камере с подписью под ней — фонарик и «Закрыть».
 *
 * Круг 60 dp: на камеру смотрят с вытянутой руки, и цель для пальца здесь крупнее, чем в
 * списках. Включённый фонарик — зелёный круг, в цвет акцента: состояние «горит» должно
 * читаться и с расстояния, на котором подпись не читается. Погашенная кнопка — тот же
 * круг вполсилы, и подпись под ней объясняет, почему нажать нельзя.
 */
@Composable
private fun CameraControl(
    @DrawableRes icon: Int,
    label: String,
    contentDescription: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (active) DesignPalette.Accent else ControlSurface.copy(alpha = ControlSurface.alpha * alpha)),
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = DesignType.Caption,
            color = Color.White.copy(alpha = alpha),
            textAlign = TextAlign.Center,
        )
    }
}

/** Плашка с сообщением сканера — белым по тёмному, в цвет остальных элементов на камере. */
@Composable
private fun MessagePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ControlSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = DesignType.Caption,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/** Что стоит на месте камеры после отказа в разрешении. */
@Composable
private fun PermissionDenied(
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(id = R.drawable.ic_qr_scanner_design),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Без камеры код не прочитать",
            style = DesignType.SheetTitle,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Разрешите приложению камеру — она нужна только здесь, пока открыт сканер.",
            style = DesignType.Body,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.Accent,
                    contentColor = DesignPalette.OnAccent,
                ),
            ) {
                Text("Разрешить", style = DesignType.ButtonLabel)
            }
            TextButton(onClick = onOpenSettings) {
                Text("Настройки приложения", style = DesignType.ButtonLabel, color = Color.White)
            }
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Экрана настроек приложения нет только на устройствах, где нет и камеры.
    }
}
