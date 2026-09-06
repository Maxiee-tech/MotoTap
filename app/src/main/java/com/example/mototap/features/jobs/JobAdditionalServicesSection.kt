package com.example.mototap.features.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mototap.core.model.JobAdditionalService
import com.example.mototap.core.util.JobAdditionalServices
import com.example.mototap.ui.theme.MotoRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JobAdditionalServicesSection(
    notes: List<JobAdditionalService>,
    canAdd: Boolean,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notes.isEmpty() && !canAdd) return

    var draft by remember { mutableStateOf("") }
    val formatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Additional services",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Text(
            text = "Record extra work done beyond the original job so the client and garage share one complete record.",
            color = Color.Gray,
            fontSize = 12.sp,
        )

        if (notes.isEmpty()) {
            Text("No additional services recorded yet.", color = Color.Gray, fontSize = 12.sp)
        } else {
            notes.forEach { note ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val whenLabel = if (note.createdAtMillis > 0) {
                        formatter.format(Date(note.createdAtMillis))
                    } else {
                        ""
                    }
                    Text(
                        text = buildString {
                            append(JobAdditionalServices.authorLabel(note))
                            if (whenLabel.isNotEmpty()) append(" · $whenLabel")
                        },
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )
                    Text(text = note.text, color = Color.White, fontSize = 13.sp)
                }
            }
        }

        if (canAdd) {
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(JobAdditionalServices.MAX_TEXT) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Describe an additional service carried out…") },
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MotoRed,
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = MotoRed,
                ),
            )
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        onAdd(text)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MotoRed),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Add to job card", fontWeight = FontWeight.Bold)
            }
        }
    }
}
