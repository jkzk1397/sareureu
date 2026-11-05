package com.capstone.pause.core.data.profile

import com.capstone.pause.core.model.Profile
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** 프로필 저장소(DataStore). 프로필이 없으면 앱은 온보딩을 먼저 보여 준다. */
interface ProfileRepository {
    /** 저장된 프로필. 온보딩 전이면 null. */
    val profile: Flow<Profile?>

    /** 온보딩을 끝내 프로필이 있는지. */
    val hasProfile: Flow<Boolean>

    /** 프로필 저장(온보딩 완료·수정). */
    suspend fun save(profile: Profile)

    /** 프로필과 동의 기록 지우기(모든 기록 삭제). */
    suspend fun clear()
}

/** 동의 항목(04 문서 4절). */
enum class ConsentItem { TERMS, USAGE_ACCESS, NOTIFICATIONS, OVERLAY, ACCESSIBILITY, AI_USE, FRIENDS, PEER_COMPARE }

/** 동의 기록 하나. [version]은 고지 문구 버전(바뀌면 다시 묻는다). */
data class ConsentRecord(val item: ConsentItem, val version: Int, val granted: Boolean, val at: Instant)

/** 동의 기록 저장소(DataStore). 항목마다 마지막 기록만 둔다. */
interface ConsentRepository {
    /** 항목별 마지막 기록. */
    val consents: Flow<Map<ConsentItem, ConsentRecord>>

    /** 기록 남기기. 뒤로 가기는 동의가 아니므로 버튼을 눌렀을 때만 부른다. */
    suspend fun record(item: ConsentItem, version: Int, granted: Boolean)
}
