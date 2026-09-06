package com.example.mototap.features.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.mototap.core.repository.ChatRepository
import com.example.mototap.core.repository.ChatSummary
import com.example.mototap.ui.theme.MotoRed
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chatRepository: ChatRepository,
    onChatSelected: (roomId: String) -> Unit,
    onBack: () -> Unit,
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val chatSummaries by chatRepository.observeChatSummaries(currentUserId)
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<ChatSummary?>(null) }

    pendingDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete conversation?") },
            text = {
                Text(
                    "Delete your conversation with ${summary.otherUserName}? " +
                        "This removes it from your messages.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            chatRepository.deleteConversation(currentUserId, summary.partnerId)
                            pendingDelete = null
                        }
                    },
                ) {
                    Text("Delete", color = MotoRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "MESSAGES",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MotoRed,
                ),
            )
        },
        containerColor = Color.Black,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (chatSummaries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No messages yet. Start a chat from a mechanic or parts dealer on the map.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(chatSummaries, key = { it.partnerId }) { summary ->
                        ChatSummaryItem(
                            summary = summary,
                            onClick = { onChatSelected(summary.roomId) },
                            onDelete = { pendingDelete = summary },
                        )
                        HorizontalDivider(
                            color = Color.DarkGray.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatSummaryItem(
    summary: ChatSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = if (summary.lastMessage.timestampMillis > 0) {
        sdf.format(Date(summary.lastMessage.timestampMillis))
    } else {
        ""
    }
    val isUnread = summary.unread
    val label = formatInboxPartnerLabel(
        summary.otherUserName,
        summary.otherUserRole,
        summary.garageName,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = Color.DarkGray.copy(alpha = 0.5f),
        ) {
            val photoUrl = summary.otherUserPhotoUrl.trim()
            if (photoUrl.isNotEmpty()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = summary.otherUserName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (timeString.isNotEmpty()) {
                    Text(
                        text = timeString,
                        color = if (isUnread) Color(0xFF25D366) else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.lastMessage.text,
                    color = if (isUnread) Color.White else Color.Gray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                )

                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(10.dp)
                            .background(Color(0xFF25D366), CircleShape),
                    )
                }
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete conversation",
                tint = Color.Gray,
            )
        }
    }
}

private fun formatInboxPartnerLabel(name: String, role: String, garageName: String = ""): String {
    val normalized = role.trim().lowercase()
    val garage = garageName.trim()
    if (garage.isNotEmpty() &&
        (normalized == "mechanic" || normalized == "parts_dealer" || normalized == "parts dealer")
    ) {
        val shopLabel = if (normalized == "mechanic") "Garage" else "Shop"
        return "$shopLabel · $garage"
    }
    val roleLabel = when (normalized) {
        "mechanic" -> "Mechanic"
        "parts_dealer", "parts dealer" -> "Parts dealer"
        "driver" -> "Driver"
        else -> if (role.isBlank()) "User" else role.replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
    }
    val display = name.ifBlank { "User" }
    return "$roleLabel · $display"
}
