package ru.zaroslikov.incubator.data.mapper

import ru.zaroslikov.incubator.data.entity.BatchEntity
import ru.zaroslikov.incubator.data.entity.IncubatorEntity
import ru.zaroslikov.incubator.data.entity.MeasurementEntity
import ru.zaroslikov.incubator.data.entity.SpeciesEntity
import ru.zaroslikov.incubator.data.entity.TimeEntity
import ru.zaroslikov.incubator.data.entity.ValueEntity
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Species
import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.model.Value

fun IncubatorEntity.toDomain(): Incubator = Incubator(
    id, name, capacity, brand, model, price, note, autoTurn, autoAiring
)

fun Incubator.toEntity(): IncubatorEntity = IncubatorEntity(
    id, name, capacity, brand, model, price, note, autoTurn, autoAiring
)

fun BatchEntity.toDomain(): Batch = Batch(
    id, title, type, data, eggAll, eggAllEND, airing, over, arhive, dateEnd, note, incubatorId,
    breed, price, pricePerEgg
)

fun Batch.toEntity(): BatchEntity = BatchEntity(
    id, title, type, data, eggAll, eggAllEND, airing, over, arhive, dateEnd, note, incubatorId,
    breed, price, pricePerEgg
)

fun ValueEntity.toDomain(): Value = Value(
    id, day, temp, damp, over, airing, note, idPT
)

fun Value.toEntity(): ValueEntity = ValueEntity(
    id, day, temp, damp, over, airing, note, idPT
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
    id, idValue, time, temp, damp, over, airing, note
)

fun Measurement.toEntity(): MeasurementEntity = MeasurementEntity(
    id, idValue, time, temp, damp, over, airing, note
)
