package com.example.mototap.features.mechanic

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mototap.core.model.ChatMessage
import com.example.mototap.ui.theme.MotoRed
import java.text.SimpleDateFormat
import java.util.*

private fun chatHeaderSubtitle(personName: String, role: String, garageName: String): String {
    if (garageName.isBlank()) return ""
    val roleLabel = when (role.trim().lowercase()) {
        "mechanic" -> "Mechanic"
        "parts_dealer", "parts dealer" -> "Parts dealer"
        "driver" -> "Driver"
        else -> if (role.isBlank()) "" else role.replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
    }
    return if (personName.isNotBlank() && personName != garageName && roleLabel.isNotBlank()) {
        "$roleLabel · $personName"
    } else {
        roleLabel
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    currentUserId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setTypingStatus(false)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val garageName = uiState.garageName.trim()
                        val personName = uiState.otherParticipantName.trim().ifBlank { "User" }
                        val title = if (garageName.isNotEmpty()) garageName else personName
                        Text(
                            text = title.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = chatHeaderSubtitle(
                            personName = personName,
                            role = uiState.otherParticipantRole,
                            garageName = garageName,
                        )
                        if (uiState.isOtherTyping) {
                            Text("typing...", color = Color.Green, fontSize = 10.sp)
                        } else if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MotoRed)
            )
        },
        containerColor = Color(0xFF0B141A)
    ) { paddingValues ->
        // Standard Column layout with imePadding on the whole container
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .imePadding() // This is the key to pushing everything up together
        ) {
            if (uiState.isOtherTyping) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    TypingIndicator(partnerName = uiState.otherParticipantName)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(uiState.messages) { message ->
                    MessageBubble(message = message, isCurrentUser = message.senderId == currentUserId)
                }
            }

            ChatInput(
                text = textState,
                onTextChange = { 
                    textState = it
                    viewModel.onInputChanged(it)
                },
                onSend = {
                    viewModel.sendMessage(textState)
                    textState = ""
                }
            )
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isCurrentUser: Boolean) {
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (isCurrentUser) Color(0xFF005C4B) else Color(0xFF202C33)
    val shape = if (isCurrentUser) RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp) else RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = alignment) {
        Column(modifier = Modifier.widthIn(max = 300.dp).background(bgColor, shape).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = message.text, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
            Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestampMillis)),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                if (isCurrentUser) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (message.read) Icons.Default.DoneAll else Icons.Default.Done,
                        contentDescription = null,
                        tint = if (message.read) Color(0xFF53BDEB) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicator(partnerName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TypingDot(delayMillis = 0)
            TypingDot(delayMillis = 200)
            TypingDot(delayMillis = 400)
        }
        Text(
            text = "$partnerName is typing",
            fontSize = 13.sp,
            color = Color(0xFF888888),
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
fun TypingDot(delayMillis: Int) {
    val infinite = rememberInfiniteTransition(label = "typing")
    val dotAlpha by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.25f at delayMillis
                1f at delayMillis + 300
                0.25f at delayMillis + 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dotAlpha"
    )
    val dotOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at delayMillis
                3f at delayMillis + 300
                0f at delayMillis + 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dotOffset"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .offset(y = (-dotOffset).dp)
            .alpha(dotAlpha)
            .background(Color(0xFF888888), CircleShape)
    )
}

@Composable
fun ChatInput(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
    // Simple Surface that respects navigation bars when keyboard is closed
    Surface(
        color = Color(0xFF1F2C34), 
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding() // Keep it above the bottom pill
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.weight(1f), 
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A3942)), 
                shape = RoundedCornerShape(25.dp)
            ) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Message", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = onSend,
                containerColor = Color(0xFF00A884),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", modifier = Modifier.size(20.dp))
            }
        }
    }
}
