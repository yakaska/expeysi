package com.expeysi.app.data.remote.firebase

import com.expeysi.app.data.remote.FamilyRemote
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-реализация [FamilyRemote]. Wire-формат прежний (docs/data-model.md):
 * документ семьи name/createdAt/createdBy, документ члена displayName/joinedAt/uid.
 * createFamily и joinFamily сознательно не ждут подтверждения — см. контракт
 * [FamilyRemote]; leaveFamily ждёт await() удаления документа.
 */
@Singleton
class FirebaseFamilyRemote @Inject constructor(
    private val firestore: FirebaseFirestore,
) : FamilyRemote {

    override suspend fun createFamily(uid: String, displayName: String): String {
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
            batch.set(family.collection("members").document(uid), memberData(uid, displayName))
        }
        return family.id
    }

    override suspend fun joinFamily(familyId: String, uid: String, displayName: String) {
        firestore.collection("families").document(familyId)
            .collection("members").document(uid)
            .set(memberData(uid, displayName))
    }

    override suspend fun leaveFamily(familyId: String, uid: String) {
        firestore.collection("families").document(familyId)
            .collection("members").document(uid)
            .delete()
            .await()
    }

    private fun memberData(uid: String, displayName: String) = mapOf(
        "displayName" to displayName,
        "joinedAt" to FieldValue.serverTimestamp(),
        "uid" to uid,
    )
}
