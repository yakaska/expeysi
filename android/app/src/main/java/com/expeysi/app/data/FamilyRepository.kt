package com.expeysi.app.data

import androidx.room.withTransaction
import com.expeysi.app.data.db.AppDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val familyStore: FamilyStore,
    private val database: AppDatabase,
) {
    suspend fun createFamily() {
        val uid = authRepository.userId ?: error("Пользователь не вошёл")
        val family = firestore.collection("families").document()
        firestore.runBatch { batch ->
            batch.set(
                family,
                mapOf(
                    "name" to "Семья",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "createdBy" to uid,
                ),
            )
            batch.set(family.collection("members").document(uid), memberData(uid))
        }
        familyStore.select(family.id)
    }

    suspend fun joinFamily(code: String) {
        val uid = authRepository.userId ?: error("Пользователь не вошёл")
        val familyId = code.trim()
        require(familyId.isNotEmpty()) { "Введите код семьи" }
        firestore.collection("families").document(familyId)
            .collection("members").document(uid)
            .set(memberData(uid))
        database.withTransaction {
            database.transactionDao().clear()
            database.accountDao().clear()
            database.categoryDao().clear()
            database.balanceDao().clear()
        }
        familyStore.select(familyId)
    }

    private fun memberData(uid: String) = mapOf(
        "displayName" to authRepository.displayName,
        "joinedAt" to FieldValue.serverTimestamp(),
        "uid" to uid,
    )
}
