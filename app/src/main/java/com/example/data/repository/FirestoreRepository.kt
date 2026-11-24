package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.Emotion
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.MessageSender
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para continuar.")
    }

    private fun conversationsCollection(uid: String) =
        db.collection("users").document(uid).collection("conversations")

    private fun messagesCollection(uid: String, conversationId: String) =
        conversationsCollection(uid).document(conversationId).collection("messages")

    private fun memoriesCollection(uid: String) =
        db.collection("users").document(uid).collection("memories")

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sesión cambiada" }
        val path = "users/$uid/conversations"
        emitAll(
            conversationsCollection(uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sesión cambiada" }
        require(conversationId.isNotBlank())
        val path = "users/$uid/conversations/$conversationId/messages"
        emitAll(
            messagesCollection(uid, conversationId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMemories(userId: String): Flow<List<MemoryItem>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sesión cambiada" }
        val path = "users/$uid/memories"
        emitAll(
            memoriesCollection(uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MemoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createConversation(
        title: String,
        initialPreview: String = "",
        initialEmotion: Emotion = Emotion.ALEGRIA
    ): Result<String> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/conversations"
        return try {
            val verifiedUid = requireUserId()
            val safeTitle = title.trim().ifBlank { "Nueva conversación" }.take(120)
            val safePreview = initialPreview.trim().take(300)
            val docRef = conversationsCollection(verifiedUid).document()
            val payload = mapOf(
                "userId" to verifiedUid,
                "title" to safeTitle,
                "lastMessagePreview" to safePreview,
                "lastEmotion" to initialEmotion.name,
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun appendMessage(
        conversationId: String,
        sender: MessageSender,
        text: String,
        emotion: Emotion,
        actionTaken: String? = null,
        updateTitleIfDefault: String? = null,
        newMessageCount: Long
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val msgPath = "users/${uid ?: "unauthenticated"}/conversations/$conversationId/messages"
        return try {
            val verifiedUid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID vacío" }
            val safeText = text.trim().take(16000)
            require(safeText.isNotEmpty()) { "El mensaje no puede estar vacío" }
            val safeAction = actionTaken?.trim()?.take(300)?.takeIf { it.isNotEmpty() }

            val convRef = conversationsCollection(verifiedUid).document(conversationId)
            val msgRef = messagesCollection(verifiedUid, conversationId).document()

            val msgPayload = mutableMapOf<String, Any?>(
                "userId" to verifiedUid,
                "conversationId" to conversationId,
                "sender" to sender.name,
                "text" to safeText,
                "emotion" to emotion.name,
                "actionTaken" to safeAction,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val convUpdate = mutableMapOf<String, Any>(
                "lastMessagePreview" to safeText.take(280),
                "lastEmotion" to emotion.name,
                "messageCount" to newMessageCount.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!updateTitleIfDefault.isNullOrBlank()) {
                convUpdate["title"] = updateTitleIfDefault.trim().take(120)
            }

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(convRef, convUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgPath)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/conversations/$conversationId"
        return try {
            val verifiedUid = requireUserId()
            require(conversationId.isNotBlank())
            val msgsSnap = messagesCollection(verifiedUid, conversationId).get().await()
            val batches = msgsSnap.documents.chunked(400)
            for (chunk in batches) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            conversationsCollection(verifiedUid).document(conversationId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllConversations(): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/conversations"
        return try {
            val verifiedUid = requireUserId()
            val convSnap = conversationsCollection(verifiedUid).get().await()
            for (doc in convSnap.documents) {
                deleteConversation(doc.id).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveOrUpdateMemory(
        existingId: String? = null,
        category: MemoryCategory,
        key: String,
        value: String,
        authorized: Boolean = true
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/memories"
        return try {
            val verifiedUid = requireUserId()
            val safeKey = key.trim().take(100)
            val safeValue = value.trim().take(500)
            require(safeKey.isNotEmpty() && safeValue.isNotEmpty()) {
                "La clave y el valor del recuerdo son obligatorios."
            }

            if (!existingId.isNullOrBlank()) {
                memoriesCollection(verifiedUid).document(existingId).update(
                    mapOf(
                        "category" to category.name,
                        "key" to safeKey,
                        "value" to safeValue,
                        "authorized" to authorized,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                // Check if same key+category exists to avoid duplicates
                val existingSnap = memoriesCollection(verifiedUid)
                    .whereEqualTo("key", safeKey)
                    .get()
                    .await()
                val match = existingSnap.documents.firstOrNull()
                if (match != null) {
                    match.reference.update(
                        mapOf(
                            "category" to category.name,
                            "value" to safeValue,
                            "authorized" to authorized,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    val docRef = memoriesCollection(verifiedUid).document()
                    docRef.set(
                        mapOf(
                            "userId" to verifiedUid,
                            "category" to category.name,
                            "key" to safeKey,
                            "value" to safeValue,
                            "authorized" to authorized,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/memories/$memoryId"
        return try {
            val verifiedUid = requireUserId()
            require(memoryId.isNotBlank())
            memoriesCollection(verifiedUid).document(memoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllMemories(): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/memories"
        return try {
            val verifiedUid = requireUserId()
            val snap = memoriesCollection(verifiedUid).get().await()
            for (chunk in snap.documents.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
