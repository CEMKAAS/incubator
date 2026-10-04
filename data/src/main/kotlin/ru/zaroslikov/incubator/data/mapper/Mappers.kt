package ru.zaroslikov.incubator.data.mapper

import ru.zaroslikov.incubator.data.entity.BatchEntity
import ru.zaroslikov.incubator.data.entity.CandlingEntity
import ru.zaroslikov.incubator.data.entity.CustomSpeciesDayEntity
import ru.zaroslikov.incubator.data.entity.CustomSpeciesEntity
import ru.zaroslikov.incubator.data.entity.CustomSpeciesWithDays
import ru.zaroslikov.incubator.data.entity.IncubatorEntity
import ru.zaroslikov.incubator.data.entity.MeasurementEntity
import ru.zaroslikov.incubator.data.entity.SpeciesEntity
import ru.zaroslikov.incubator.data.entity.TimeEntity
import ru.zaroslikov.incubator.data.entity.UserEntity
import ru.zaroslikov.incubator.data.entity.ValueEntity
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.domain.model.CustomSpeciesDay
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.PowerSettings
import ru.zaroslikov.incubator.domain.model.Species
import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.model.User
import ru.zaroslikov.incubator.domain.model.Value

fun IncubatorEntity.toDomain(): Incubator = Incubator(
    id, name, capacity, brand, model, price, note, autoTurn, autoAiring, hidden,
    power = PowerSettings(powerWatts, tariffDay, tariffNight, nightStart, nightEnd),
)

fun Incubator.toEntity(): IncubatorEntity = IncubatorEntity(
    id, name, capacity, brand, model, price, note, autoTurn, autoAiring, hidden,
    powerWatts = power.watts,
    tariffDay = power.dayPrice,
    tariffNight = power.nightPrice,
    nightStart = power.nightStart,
    nightEnd = power.nightEnd,
)

fun BatchEntity.toDomain(): Batch = Batch(
    id, title, type, data, eggAll, eggAllEND, airing, over, arhive, dateEnd, note, incubatorId,
    price, pricePerEgg, endReason, chickPrice, chickPricePerHead, hidden, time, eggRejected,
    breed = breed,
    power = PowerSettings(powerWatts, tariffDay, tariffNight, nightStart, nightEnd),
    timeEnd = timeEnd,
)

fun Batch.toEntity(): BatchEntity = BatchEntity(
    id, title, type, data, eggAll, eggAllEND, airing, over, arhive, dateEnd, note, incubatorId,
    breed, price, pricePerEgg, endReason, chickPrice, chickPricePerHead, hidden, time, eggRejected,
    powerWatts = power.watts,
    tariffDay = power.dayPrice,
    tariffNight = power.nightPrice,
    nightStart = power.nightStart,
    nightEnd = power.nightEnd,
    timeEnd = timeEnd,
)

fun ValueEntity.toDomain(): Value = Value(
    id, day, temp, damp, over, airingCount, airingTime, note, idPT
)

fun Value.toEntity(): ValueEntity = ValueEntity(
    id, day, temp, damp, over, airingCount, airingTime, note, idPT
)

fun TimeEntity.toDomain(): Time = Time(
    id, time, idPT, note
)

fun Time.toEntity(): TimeEntity = TimeEntity(
    id, time, idPT, note
)

fun SpeciesEntity.toDomain(): Species = Species(
    id, species, idPT
)

fun Species.toEntity(): SpeciesEntity = SpeciesEntity(
    id, species, idPT
)

fun MeasurementEntity.toDomain(): Measurement = Measurement(
    id, idValue, time, temp, damp, over, airingCount, airingTime, note, groupId
)

fun Measurement.toEntity(): MeasurementEntity = MeasurementEntity(
    id, idValue, time, temp, damp, over, airingCount, airingTime, note, groupId
)

fun CandlingEntity.toDomain(): Candling = Candling(
    id, idPT, day, date, rejected
)

fun Candling.toEntity(): CandlingEntity = CandlingEntity(
    id, idPT, day, date, rejected
)

fun UserEntity.toDomain(): User = User(name)

/**
 * Ключ не переносится, а ставится константой: строка в таблице одна, и её адрес —
 * свойство хранилища, а не факт о пользователе, которому в доменной модели не место.
 */
fun User.toEntity(): UserEntity = UserEntity(UserEntity.SINGLE_ROW_ID, name)

/**
 * Дни сортируются здесь, а не запросом: `@Relation` порядок дочерних строк не задаёт,
 * а вид без порядка дней бессмыслен — по нему считают и срок, и режим N-го дня.
 */
fun CustomSpeciesWithDays.toDomain(): CustomSpecies = CustomSpecies(
    id = species.id,
    name = species.name,
    days = days.sortedBy { it.day }.map { it.toDomain() },
)

fun CustomSpecies.toEntity(): CustomSpeciesEntity = CustomSpeciesEntity(id, name)

fun CustomSpeciesDayEntity.toDomain(): CustomSpeciesDay = CustomSpeciesDay(
    id, speciesId, day, temp, damp, over, airingCount, airingTime, candling
)

fun CustomSpeciesDay.toEntity(): CustomSpeciesDayEntity = CustomSpeciesDayEntity(
    id, speciesId, day, temp, damp, over, airingCount, airingTime, candling
)
