package com.apoorvdarshan.calorietracker.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.WeightEntry
import com.apoorvdarshan.calorietracker.ui.components.LocalEnergyUnit
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class CutWeightChange(val firstAverage: Double, val lastAverage: Double, val firstDays: Int, val lastDays: Int) {
    val lostKg: Double get() = firstAverage - lastAverage
}

internal fun cutWeightChange(weights: List<WeightEntry>, start: LocalDate, end: LocalDate, zone: ZoneId): CutWeightChange? {
    val length = end.toEpochDay() - start.toEpochDay() + 1
    if (length < 4) return null
    val window = minOf(7L, length / 2)
    val daily = weights.filter { it.weightKg.isFinite() && it.weightKg > 0 }
        .groupBy { it.date.atZone(zone).toLocalDate() }
        .mapValues { (_, readings) -> readings.map { it.weightKg }.average() }
    val first = daily.filterKeys { !it.isBefore(start) && it.isBefore(start.plusDays(window)) }.values
    val last = daily.filterKeys { it.isAfter(end.minusDays(window)) && !it.isAfter(end) }.values
    if (first.size < 2 || last.size < 2) return null
    return CutWeightChange(first.average(), last.average(), first.size, last.size)
}

internal fun dailyCutDeficits(days: List<Pair<LocalDate, Int>>, maintenance: Int): List<Pair<LocalDate, Int>> =
    days.groupBy({ it.first }, { it.second }).toSortedMap().map { (day, values) ->
        day to (maintenance.toLong() - values.sumOf { it.toLong() }).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

@Composable
internal fun CutSection(
    dailyCalories: List<Pair<LocalDate, Int>>, weights: List<WeightEntry>, maintenance: Int,
    start: LocalDate, end: LocalDate, useMetric: Boolean
) {
    val zone = ZoneId.systemDefault()
    val deficits = remember(dailyCalories, maintenance) { dailyCutDeficits(dailyCalories, maintenance) }
    val change = remember(weights, start, end, zone) { cutWeightChange(weights, start, end, zone) }
    val energy = LocalEnergyUnit.current
    val energySuffix = if (energy.displayValue(100) == 100) "kcal" else "kJ"
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val positive = Color(0xFF007F86)
    val negative = Color(0xFFC3511D)
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.progress_cut_title), style = MaterialTheme.typography.titleMedium)
        Text("${start.format(dateFormat)} - ${end.format(dateFormat)}", style = MaterialTheme.typography.bodySmall)
        if (deficits.isEmpty()) Text(stringResource(R.string.progress_no_food))
        else {
            val average = energy.displayValue(deficits.map { it.second.toDouble() }.average().roundToInt())
            val summary = stringResource(R.string.progress_cut_average, average, energySuffix)
            Text(summary)
            val values = deficits.map { it.second.toDouble() } + 0.0
            val minimum = values.minOrNull() ?: 0.0
            val maximum = values.maxOrNull() ?: 0.0
            val span = (maximum - minimum).coerceAtLeast(1.0)
            val dayCount = (end.toEpochDay() - start.toEpochDay() + 1).coerceAtLeast(1)
            Text("${energy.displayValue(maximum.toInt())} / ${energy.displayValue(minimum.toInt())} $energySuffix",
                style = MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = summary }) {
                val zero = (maximum / span * size.height).toFloat()
                val slot = size.width / dayCount.toFloat()
                drawLine(grid, Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
                deficits.forEach { (day, deficit) ->
                    val top = ((maximum - deficit) / span * size.height).toFloat()
                    drawRect(if (deficit >= 0) positive else negative,
                        Offset((day.toEpochDay() - start.toEpochDay()).toFloat() * slot, minOf(top, zero)),
                        Size((slot * 0.8f).coerceAtLeast(0.5f), abs(top - zero)))
                }
            }
            Text(stringResource(R.string.progress_cut_basis, energy.displayValue(maintenance), energySuffix,
                deficits.size, dayCount.toInt()), style = MaterialTheme.typography.bodySmall)
        }
        if (change == null) Text(stringResource(R.string.progress_cut_weight_missing), style = MaterialTheme.typography.bodySmall)
        else {
            val factor = if (useMetric) 1.0 else 2.20462
            val unit = if (useMetric) "kg" else "lb"
            fun formatted(value: Double) = String.format(Locale.getDefault(), "%.2f", value * factor)
            Text(stringResource(R.string.progress_energy_weight_loss, formatted(change.lostKg), unit), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.progress_cut_weight_averages, formatted(change.firstAverage), formatted(change.lastAverage),
                unit, change.firstDays, change.lastDays), style = MaterialTheme.typography.bodySmall)
        }
    }
}