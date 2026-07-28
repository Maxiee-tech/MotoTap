package com.example.mototap.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mototap.core.util.DayHours
import com.example.mototap.core.util.WEEKDAY_KEYS
import com.example.mototap.core.util.WEEKDAY_LABELS
import com.example.mototap.core.util.WorkingHours
import com.example.mototap.core.util.defaultWorkingHours

@Composable
fun WorkingHoursEditor(
    hours: WorkingHours,
    onHoursChange: (WorkingHours) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = if (hours.days.isEmpty()) defaultWorkingHours() else hours
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Working hours (24-hour)",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Text(
            text = "Use times like 08:00–18:00. Mark a day Closed if you do not open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WEEKDAY_KEYS.forEach { key ->
            val day = current.days[key] ?: DayHours(closed = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = WEEKDAY_LABELS[key] ?: key,
                    modifier = Modifier.weight(1.1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = day.closed,
                        onCheckedChange = { closed ->
                            onHoursChange(
                                current.copy(
                                    days = current.days + (key to day.copy(closed = closed)),
                                ),
                            )
                        },
                    )
                    Text("Closed", fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = day.open,
                    onValueChange = { value ->
                        onHoursChange(
                            current.copy(
                                days = current.days + (key to day.copy(open = value.trim())),
                            ),
                        )
                    },
                    enabled = !day.closed,
                    singleLine = true,
                    label = { Text("Open") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = day.close,
                    onValueChange = { value ->
                        onHoursChange(
                            current.copy(
                                days = current.days + (key to day.copy(close = value.trim())),
                            ),
                        )
                    },
                    enabled = !day.closed,
                    singleLine = true,
                    label = { Text("Close") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}
