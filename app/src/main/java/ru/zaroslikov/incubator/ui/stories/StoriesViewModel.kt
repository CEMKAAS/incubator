package ru.zaroslikov.incubator.ui.stories

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.stories.StoriesRepository
import ru.zaroslikov.incubator.stories.Story
import ru.zaroslikov.incubator.ui.mvi.MviSharing
import ru.zaroslikov.incubator.ui.mvi.MviViewModel

/** Лента историй над списком инкубаторов. Пустая — строки нет вовсе. */
@Immutable
data class StoriesState(val stories: List<Story> = emptyList())

sealed interface StoriesIntent {
    /** Экран вышел на передний план: лента обновляется, если устарела. */
    data object Refresh : StoriesIntent

    /** История досмотрена до последнего слайда. */
    data class Viewed(val storyId: String) : StoriesIntent
}

/** Эффектов нет: просмотрщик открывает и закрывает сам экран. */
sealed interface StoriesEffect

/**
 * Список-экран в смысле MVI: своего состояния не держит, ленту целиком отдаёт
 * [StoriesRepository], который живёт в контейнере. Запрос — из [StoriesIntent.Refresh],
 * который экран шлёт на каждом возвращении; решать, пора ли идти в сеть, — дело
 * репозитория.
 */
class StoriesViewModel(
    private val repository: StoriesRepository,
) : MviViewModel<StoriesState, StoriesIntent, StoriesEffect>() {

    override val state: StateFlow<StoriesState> = repository.feed
        .map { StoriesState(it.stories) }
        .stateIn(viewModelScope, MviSharing.WhileVisible, StoriesState(repository.feed.value.stories))

    override fun onIntent(intent: StoriesIntent) {
        when (intent) {
            StoriesIntent.Refresh -> viewModelScope.launch { repository.refresh() }
            is StoriesIntent.Viewed -> repository.markViewed(intent.storyId)
        }
    }
}
