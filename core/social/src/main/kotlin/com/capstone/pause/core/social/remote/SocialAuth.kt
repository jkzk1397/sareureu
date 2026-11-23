package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.AppLog
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 친구 서버용 로그인. Firebase **익명 로그인**만 쓴다: 이메일·비밀번호·전화번호를 받지 않는다.
 * 서버는 ID 토큰으로 사용자 ID(uid)만 안다(server/src/firebase-auth.mjs).
 */
interface SocialAuth {
    /**
     * 서버에 보낼 ID 토큰. 처음이면 익명 로그인부터. 실패하면 null(앱은 가상 친구로 계속).
     * [forceRefresh]는 서버가 401을 줬을 때 토큰을 새로 받아 한 번만 다시 해 보려고 쓴다.
     */
    suspend fun idToken(forceRefresh: Boolean = false): String?

    /** 로그인 정보를 지운다(모든 기록 삭제 때). */
    fun signOut()

    /**
     * 익명 계정 자체를 지운다(서버의 내 기록을 지운 뒤). 익명 계정은 되찾을 수 없어 남겨 둘 이유가 없다.
     * 지우지 못해도 로그아웃은 한다. 지웠으면 true.
     */
    suspend fun deleteAccount(): Boolean = false
}

/** Firebase Auth 익명 로그인. 친구 기능을 실제로 쓸 때(동의 후)만 부른다. */
@Singleton
internal class FirebaseAnonymousAuth @Inject constructor(private val log: AppLog) : SocialAuth {
    private val auth: FirebaseAuth? by lazy { runCatching { FirebaseAuth.getInstance() }.getOrNull() }

    override suspend fun idToken(forceRefresh: Boolean): String? {
        val a = auth ?: return null
        val user = a.currentUser ?: signIn(a) ?: return null
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(forceRefresh)
                .addOnSuccessListener { cont.resume(it.token) }
                .addOnFailureListener {
                    log.w(TAG, "id token failed", it)
                    cont.resume(null)
                }
        }
    }

    private suspend fun signIn(a: FirebaseAuth) = suspendCancellableCoroutine { cont ->
        a.signInAnonymously()
            .addOnSuccessListener { cont.resume(it.user) }
            .addOnFailureListener {
                // 콘솔에서 익명 로그인이 꺼져 있으면 여기로 온다(ADMIN_RESTRICTED_OPERATION).
                log.w(TAG, "anonymous sign-in failed", it)
                cont.resume(null)
            }
    }

    override fun signOut() {
        auth?.signOut()
    }

    override suspend fun deleteAccount(): Boolean {
        val a = auth ?: return false
        val user = a.currentUser ?: return true
        val deleted = suspendCancellableCoroutine { cont ->
            user.delete()
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener {
                    log.w(TAG, "account delete failed", it)
                    cont.resume(false)
                }
        }
        a.signOut()
        return deleted
    }

    private companion object {
        const val TAG = "SocialAuth"
    }
}
