package com.example.mototap.core.data.firebase

import android.util.Log
import com.example.mototap.core.model.ChatMessage
import com.example.mototap.core.repository.ChatRepository
import com.example.mototap.core.repository.ChatSummary
import com.example.mototap.core.util.ChatIds
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Chat storage aligned with mototap_web `FirebaseChatService.js`:
 * - Canonical room: `chat_{sortedUidA}_{sortedUidB}`
 * - Dual-write/read legacy room id variants + `chatMessages`
 * - Inbox via `users/{uid}/chatPartners/{partnerId}`
 */
class FirestoreChatRepository(
    private val firestore: FirebaseFirestore,
) : ChatRepository {

    private val chats = firestore.collection("chats")
    private val legacyMessages = firestore.collection("chatMessages")
    private val garageNameCache = ConcurrentHashMap<String, String>()

    override fun observeMessages(
        roomId: String,
        currentUserId: String,
    ): Flow<List<ChatMessage>> = callbackFlow {
        val trimmed = roomId.trim()
        if (trimmed.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val participants = resolveParticipants(trimmed, currentUserId)
        val roomIds = if (participants.size >= 2) {
            ChatIds.allRoomIds(participants[0], participants[1])
        } else {
            listOf(trimmed)
        }

        val subcollectionByRoom = mutableMapOf<String, List<ChatMessage>>()
        val legacyByConversation = mutableMapOf<String, List<ChatMessage>>()
        var legacyByParticipants: List<ChatMessage> = emptyList()
        val registrations = mutableListOf<ListenerRegistration>()

        fun emit() {
            val merged = dedupeMessages(
                subcollectionByRoom.values.flatten() +
                    legacyByConversation.values.flatten() +
                    legacyByParticipants
            ).sortedBy { it.timestampMillis }
            trySend(merged)
        }

        roomIds.forEach { id ->
            registrations += chats.document(id).collection("messages")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FirestoreChatRepo", "messages/$id: ${error.message}")
                        return@addSnapshotListener
                    }
                    subcollectionByRoom[id] = snapshot?.documents
                        ?.mapNotNull { it.toChatMessageOrNull(source = "subcollection") }
                        .orEmpty()
                    emit()
                }
        }

        roomIds.forEach { conversationId ->
            registrations += legacyMessages
                .whereEqualTo("conversationId", conversationId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("FirestoreChatRepo", "legacy/$conversationId: ${error.message}")
                        return@addSnapshotListener
                    }
                    legacyByConversation[conversationId] = snapshot?.documents
                        ?.mapNotNull { it.toChatMessageOrNull(source = "legacy") }
                        .orEmpty()
                    emit()
                }
        }

        val me = currentUserId.takeIf { it.isNotBlank() }
        if (participants.size >= 2 && !me.isNullOrBlank()) {
            val partnerKey = participants.sorted().joinToString("|")
            registrations += legacyMessages
                .whereArrayContains("participantIds", me)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("FirestoreChatRepo", "legacy participants: ${error.message}")
                        return@addSnapshotListener
                    }
                    legacyByParticipants = snapshot?.documents
                        ?.mapNotNull { doc ->
                            val ids = (doc.get("participantIds") as? List<*>)
                                ?.mapNotNull { it as? String }
                                ?.sorted()
                                .orEmpty()
                            if (ids.joinToString("|") != partnerKey) return@mapNotNull null
                            doc.toChatMessageOrNull(source = "legacy")
                        }
                        .orEmpty()
                    emit()
                }
        }

        awaitClose { registrations.forEach { it.remove() } }
    }

    override suspend fun sendMessage(
        roomId: String,
        senderId: String,
        text: String,
        senderName: String?,
        recipientName: String?,
    ): Result<Unit> = runCatching {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Message cannot be empty." }
        require(trimmed.length <= ChatIds.MAX_MESSAGE_LENGTH) {
            "Message must be ${ChatIds.MAX_MESSAGE_LENGTH} characters or fewer."
        }

        val participants = resolveParticipants(roomId, senderId)
        require(participants.size >= 2) { "Invalid conversation." }
        val partnerId = participants.first { it != senderId }
        val roomIds = ChatIds.allRoomIds(participants[0], participants[1])
        val canonical = ChatIds.roomId(participants[0], participants[1])
        val now = System.currentTimeMillis()
        val sortedParticipants = participants.sorted()

        val resolvedSenderName = senderName?.takeIf { it.isNotBlank() }
            ?: resolveDisplayName(senderId)
        val resolvedPartnerName = recipientName?.takeIf { it.isNotBlank() }
            ?: resolveDisplayName(partnerId)

        val messagePayload = mapOf(
            "senderId" to senderId,
            "text" to trimmed,
            "timestampMillis" to now,
            "read" to false,
            "participantIds" to sortedParticipants,
        )
        val parentUpdate = mapOf(
            "participants" to sortedParticipants,
            "participantNames" to mapOf(
                senderId to resolvedSenderName,
                partnerId to resolvedPartnerName,
            ),
            "lastActiveMillis" to now,
            "lastActive" to now,
            "lastMessageText" to trimmed,
            "lastMessageSenderId" to senderId,
            "lastMessageMillis" to now,
        )

        var wroteRoom = false
        var wroteLegacy = false
        roomIds.forEach { id ->
            runCatching {
                chats.document(id).set(parentUpdate, SetOptions.merge()).await()
                chats.document(id).collection("messages").add(messagePayload).await()
                wroteRoom = true
            }.onFailure {
                Log.w("FirestoreChatRepo", "Room write failed for $id: ${it.message}")
            }
            runCatching {
                legacyMessages.add(
                    hashMapOf<String, Any>(
                        "senderId" to senderId,
                        "text" to trimmed,
                        "timestampMillis" to now,
                        "read" to false,
                        "participantIds" to sortedParticipants,
                        "conversationId" to id,
                    )
                ).await()
                wroteLegacy = true
            }.onFailure {
                Log.w("FirestoreChatRepo", "Legacy write failed for $id: ${it.message}")
            }
        }

        if (!wroteRoom && !wroteLegacy) {
            error("Failed to send message")
        }

        syncChatPartnerEntries(
            participants = sortedParticipants,
            participantNames = mapOf(
                senderId to resolvedSenderName,
                partnerId to resolvedPartnerName,
            ),
            preview = trimmed,
            millis = now,
            senderId = senderId,
            roomId = canonical,
        )
    }

    override suspend fun markAsRead(roomId: String, currentUserId: String): Result<Unit> = runCatching {
        val participants = resolveParticipants(roomId, currentUserId)
        val roomIds = if (participants.size >= 2) {
            ChatIds.allRoomIds(participants[0], participants[1])
        } else {
            listOf(roomId.trim())
        }
        val partnerId = participants.firstOrNull { it != currentUserId }

        roomIds.forEach { id ->
            val messages = chats.document(id).collection("messages").get().await()
            val unread = messages.documents.filter { doc ->
                val senderId = doc.getString("senderId").orEmpty()
                val read = doc.getBoolean("read") ?: false
                senderId.isNotEmpty() && senderId != currentUserId && !read
            }
            if (unread.isEmpty()) return@forEach
            val batch = firestore.batch()
            unread.forEach { doc -> batch.update(doc.reference, "read", true) }
            batch.set(
                chats.document(id),
                mapOf("lastReadUpdate" to System.currentTimeMillis()),
                SetOptions.merge(),
            )
            runCatching { batch.commit().await() }
        }

        if (!partnerId.isNullOrBlank()) {
            firestore.collection("users").document(currentUserId)
                .collection("chatPartners").document(partnerId)
                .set(
                    mapOf("lastReadAtMillis" to System.currentTimeMillis()),
                    SetOptions.merge(),
                ).await()
        }
    }

    override fun observeChatSummaries(userId: String): Flow<List<ChatSummary>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var enrichJob: Job? = null

        val subscription = firestore.collection("users").document(userId)
            .collection("chatPartners")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreChatRepo", "chatPartners: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val entries = snapshot?.documents.orEmpty()
                if (entries.isEmpty()) {
                    enrichJob?.cancel()
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                enrichJob?.cancel()
                enrichJob = scope.launch {
                    val byPartner = linkedMapOf<String, ChatSummary>()
                    entries.forEach { doc ->
                        val partnerId = doc.getString("partnerId") ?: doc.id
                        if (partnerId.isBlank()) return@forEach
                        val roomId = doc.getString("roomId")
                            ?.takeIf { it.isNotBlank() }
                            ?: ChatIds.roomId(userId, partnerId)
                        val text = doc.getString("lastMessageText").orEmpty()
                        val senderId = doc.getString("lastMessageSenderId").orEmpty()
                        val millis = doc.getLong("lastMessageMillis")
                            ?: doc.getLong("updatedAtMillis")
                            ?: 0L
                        val lastRead = doc.getLong("lastReadAtMillis") ?: 0L
                        val unread = senderId.isNotEmpty() &&
                            senderId != userId &&
                            millis > lastRead
                        val name = doc.getString("partnerName")?.takeIf { it.isNotBlank() }
                            ?: "MotoTap User"
                        val role = (doc.getString("partnerRole") ?: doc.getString("role") ?: "")
                            .trim()
                            .lowercase()
                        val photoUrl = doc.getString("partnerPhotoUrl")
                            ?.takeIf { it.isNotBlank() }
                            ?: resolvePartnerPhotoUrl(partnerId)
                        val garageName = doc.getString("partnerGarageName")
                            ?.takeIf { it.isNotBlank() }
                            ?: if (isBusinessRole(role)) resolveGarageName(partnerId) else ""

                        byPartner[partnerId] = ChatSummary(
                            roomId = roomId,
                            partnerId = partnerId,
                            lastMessage = ChatMessage(
                                id = "preview_$partnerId",
                                senderId = senderId,
                                text = text.ifBlank { "Tap to open conversation" },
                                timestampMillis = millis,
                                read = !unread,
                            ),
                            otherUserName = name,
                            otherUserRole = role,
                            otherUserPhotoUrl = photoUrl,
                            garageName = garageName,
                            unread = unread,
                        )
                    }

                    trySend(
                        byPartner.values.sortedByDescending { it.lastMessage.timestampMillis }
                    )
                }
            }

        awaitClose {
            enrichJob?.cancel()
            scope.cancel()
            subscription.remove()
        }
    }

    override suspend fun setTypingStatus(
        roomId: String,
        userId: String,
        isTyping: Boolean,
    ): Result<Unit> = runCatching {
        val participants = resolveParticipants(roomId, userId)
        if (participants.size < 2) return@runCatching
        val partnerId = participants.first { it != userId }
        val roomIds = ChatIds.allRoomIds(participants[0], participants[1])
        val now = System.currentTimeMillis()
        val base = mapOf(
            "lastActiveMillis" to now,
            "lastActive" to now,
            "participants" to participants.sorted(),
        )

        roomIds.forEach { id ->
            val roomRef = chats.document(id)
            roomRef.set(base, SetOptions.merge()).await()
            roomRef.update("typingStatus.$userId", isTyping).await()
        }

        firestore.collection("users").document(partnerId)
            .collection("chatPartners").document(userId)
            .set(
                mapOf(
                    "partnerId" to userId,
                    "partnerIsTyping" to isTyping,
                    "partnerTypingAtMillis" to now,
                ),
                SetOptions.merge(),
            ).await()
    }

    override fun observeTypingStatus(
        roomId: String,
        currentUserId: String,
    ): Flow<Boolean> = callbackFlow {
        val participants = resolveParticipants(roomId, currentUserId)
        if (participants.size < 2) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val partnerId = participants.first { it != currentUserId }
        val roomIds = ChatIds.allRoomIds(participants[0], participants[1])
        val typingStatuses = mutableMapOf<String, Boolean>()
        val registrations = mutableListOf<ListenerRegistration>()

        fun emit() {
            trySend(typingStatuses.values.any { it })
        }

        roomIds.forEach { id ->
            registrations += chats.document(id).addSnapshotListener { snapshot, _ ->
                val typingMap = snapshot?.get("typingStatus") as? Map<*, *>
                val status = typingMap?.get(partnerId)
                val isTyping = when (status) {
                    true -> true
                    1, 1L -> true
                    "true" -> true
                    else -> false
                }
                typingStatuses["room_$id"] = isTyping
                emit()
            }
        }

        registrations += firestore.collection("users").document(currentUserId)
            .collection("chatPartners").document(partnerId)
            .addSnapshotListener { snapshot, _ ->
                val partnerIsTyping = snapshot?.getBoolean("partnerIsTyping") ?: false
                val at = snapshot?.getLong("partnerTypingAtMillis") ?: 0L
                val fresh = System.currentTimeMillis() - at < 10_000
                typingStatuses["inbox"] = partnerIsTyping && fresh
                emit()
            }

        awaitClose { registrations.forEach { it.remove() } }
    }

    override suspend fun deleteConversation(userId: String, partnerId: String): Result<Unit> =
        runCatching {
            require(userId.isNotBlank() && partnerId.isNotBlank()) {
                "Missing conversation reference."
            }
            firestore.collection("users").document(userId)
                .collection("chatPartners").document(partnerId)
                .delete()
                .await()
        }

    override suspend fun ensureConversation(
        userId: String,
        partnerId: String,
        participantNames: Map<String, String>,
    ): Result<String> = runCatching {
        require(userId.isNotBlank() && partnerId.isNotBlank()) { "Invalid participants." }
        val roomIds = ChatIds.allRoomIds(userId, partnerId)
        val canonical = ChatIds.roomId(userId, partnerId)
        val now = System.currentTimeMillis()
        val sorted = listOf(userId, partnerId).sorted()
        val names = participantNames.filterValues { it.isNotBlank() }

        roomIds.forEach { id ->
            chats.document(id).set(
                mapOf(
                    "participants" to sorted,
                    "participantNames" to names,
                    "lastActiveMillis" to now,
                    "lastActive" to now,
                ),
                SetOptions.merge(),
            ).await()
        }

        syncChatPartnerEntries(
            participants = sorted,
            participantNames = names.ifEmpty {
                mapOf(
                    userId to resolveDisplayName(userId),
                    partnerId to resolveDisplayName(partnerId),
                )
            },
            preview = "",
            millis = now,
            senderId = "",
            roomId = canonical,
        )
        canonical
    }

    override suspend fun resolvePartnerName(
        currentUserId: String,
        partnerId: String,
        roomId: String,
    ): String {
        if (partnerId.isBlank()) return "User"

        // 1) Inbox entry written by web/app on open/send
        runCatching {
            val entry = firestore.collection("users").document(currentUserId)
                .collection("chatPartners").document(partnerId)
                .get()
                .await()
            entry.getString("partnerName")?.takeIf { it.isNotBlank() && it != "User" }?.let {
                return it
            }
        }

        // 2) Chat room participantNames (any room-id variant)
        val roomIds = buildList {
            if (roomId.isNotBlank()) add(roomId.trim())
            if (currentUserId.isNotBlank()) {
                addAll(ChatIds.allRoomIds(currentUserId, partnerId))
            }
        }.distinct()
        for (id in roomIds) {
            runCatching {
                val snap = chats.document(id).get().await()
                val names = snap.get("participantNames") as? Map<*, *>
                names?.get(partnerId)?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() && it != "User" }
                    ?.let { return it }
            }
        }

        // 3) publicProfiles / users (same as website)
        return resolveDisplayName(partnerId)
    }

    override suspend fun resolvePartnerGarageName(partnerId: String): String =
        resolveGarageName(partnerId)

    override suspend fun resolvePartnerRole(partnerId: String): String =
        resolveRole(partnerId)

    private suspend fun syncChatPartnerEntries(
        participants: List<String>,
        participantNames: Map<String, String>,
        preview: String,
        millis: Long,
        senderId: String,
        roomId: String,
    ) {
        if (participants.size < 2) return
        val userA = participants[0]
        val userB = participants[1]
        val pairs = listOf(userA to userB, userB to userA)
        pairs.forEach { (ownerId, partnerId) ->
            val partnerPhoto = resolvePartnerPhotoUrl(partnerId)
            val partnerRole = resolveRole(partnerId)
            val payload = mutableMapOf<String, Any>(
                "partnerId" to partnerId,
                "partnerName" to (participantNames[partnerId] ?: "User"),
                "partnerRole" to partnerRole,
                "participantIds" to participants.sorted(),
                "roomId" to roomId,
                "updatedAtMillis" to millis,
            )
            if (isBusinessRole(partnerRole)) {
                resolveGarageName(partnerId).takeIf { it.isNotBlank() }?.let {
                    payload["partnerGarageName"] = it.take(120)
                }
            }
            if (partnerPhoto.isNotBlank()) {
                payload["partnerPhotoUrl"] = partnerPhoto
            }
            preview.trim().takeIf { it.isNotEmpty() }?.let {
                payload["lastMessageText"] = it
                payload["lastMessageSenderId"] = senderId
                payload["lastMessageMillis"] = millis
            }
            firestore.collection("users").document(ownerId)
                .collection("chatPartners").document(partnerId)
                .set(payload, SetOptions.merge())
                .await()
        }
    }

    private fun resolveParticipants(roomId: String, knownUserId: String = ""): List<String> {
        val partner = ChatIds.partnerIdFromRoomId(roomId, knownUserId)
        return when {
            knownUserId.isNotBlank() && !partner.isNullOrBlank() ->
                listOf(knownUserId, partner)
            else -> {
                val parts = roomId.trim().split("_")
                if (parts.size >= 3 && parts[0] == "chat") listOf(parts[1], parts[2])
                else emptyList()
            }
        }
    }

    private fun isBusinessRole(role: String): Boolean {
        val value = role.trim().lowercase()
        return value == "mechanic" || value == "parts_dealer" || value == "parts dealer"
    }

    private suspend fun resolveGarageName(userId: String): String {
        if (userId.isBlank()) return ""
        garageNameCache[userId]?.let { return it }

        var resolved = ""
        runCatching {
            val public = firestore.collection("publicProfiles").document(userId).get().await()
            val garageId = public.getString("garageId")?.trim().orEmpty()
            if (garageId.isNotEmpty()) {
                garageNameCache["g:$garageId"]?.let { cached ->
                    garageNameCache[userId] = cached
                    return cached
                }
                val garage = firestore.collection("garages").document(garageId).get().await()
                garage.getString("name")?.trim()?.takeIf { it.isNotBlank() }?.let { resolved = it }
            }
            if (resolved.isBlank()) {
                resolved = public.getString("institutionName")?.trim().orEmpty()
            }
        }
        if (resolved.isBlank()) {
            runCatching {
                val user = firestore.collection("users").document(userId).get().await()
                resolved = user.getString("institutionName")?.trim().orEmpty()
            }
        }
        if (resolved.isNotBlank()) {
            garageNameCache[userId] = resolved
        }
        return resolved
    }

    private suspend fun resolveDisplayName(userId: String): String {
        if (userId.isBlank()) return "User"
        runCatching {
            val public = firestore.collection("publicProfiles").document(userId).get().await()
            public.getString("name")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        runCatching {
            val user = firestore.collection("users").document(userId).get().await()
            user.getString("name")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return "MotoTap User"
    }

    private suspend fun resolveRole(userId: String): String {
        if (userId.isBlank()) return "driver"
        runCatching {
            val public = firestore.collection("publicProfiles").document(userId).get().await()
            public.getString("role")?.takeIf { it.isNotBlank() }?.let { return it.lowercase() }
        }
        runCatching {
            val user = firestore.collection("users").document(userId).get().await()
            user.getString("role")?.takeIf { it.isNotBlank() }?.let { return it.lowercase() }
        }
        return "driver"
    }

    private suspend fun resolvePartnerPhotoUrl(userId: String): String {
        if (userId.isBlank()) return ""
        runCatching {
            val public = firestore.collection("publicProfiles").document(userId).get().await()
            public.getString("profilePhotoUrl")?.takeIf { it.isNotBlank() }?.let { return it }
            public.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        runCatching {
            val user = firestore.collection("users").document(userId).get().await()
            user.getString("profilePhotoUrl")?.takeIf { it.isNotBlank() }?.let { return it }
            user.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun DocumentSnapshot.toChatMessageOrNull(source: String): ChatMessage? {
        val senderId = getString("senderId") ?: return null
        return ChatMessage(
            id = if (source == "legacy") "legacy_$id" else id,
            senderId = senderId,
            text = getString("text") ?: "",
            timestampMillis = getLong("timestampMillis") ?: 0L,
            read = getBoolean("read") ?: false,
        )
    }

    private fun dedupeMessages(messages: List<ChatMessage>): List<ChatMessage> {
        val byKey = linkedMapOf<String, ChatMessage>()
        messages.forEach { message ->
            val key = "${message.senderId}|${message.text}|${message.timestampMillis}"
            val existing = byKey[key]
            if (existing == null) {
                byKey[key] = message
            } else if (existing.id.startsWith("legacy_") && !message.id.startsWith("legacy_")) {
                byKey[key] = message
            }
        }
        return byKey.values.toList()
    }
}
