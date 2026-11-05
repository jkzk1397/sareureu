package com.capstone.pause.core.data.message

import com.capstone.pause.core.data.session.activeSource
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.database.dao.MessageDao
import com.capstone.pause.core.database.entity.MessageEntity
import com.capstone.pause.core.model.BuddyMessage
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.Sender
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** 르르 메시지 스레드(메시지 알림이 코치 대화방에 쌓인다). */
interface MessageRepository {
    /** 최근 메시지(최신순). */
    fun observeRecent(limit: Int): Flow<List<BuddyMessage>>

    /** 메시지 저장. */
    suspend fun add(message: BuddyMessage, source: DataSource): Long

    /** 출처별 지우기. */
    suspend fun deleteAll(source: DataSource)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class RoomMessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val settingsRepository: SettingsRepository,
) : MessageRepository {

    override fun observeRecent(limit: Int): Flow<List<BuddyMessage>> =
        settingsRepository.activeSource().flatMapLatest { source ->
            messageDao.observeRecent(source, limit).map { rows -> rows.map { it.toModel() } }
        }

    override suspend fun add(message: BuddyMessage, source: DataSource): Long {
        val (type, friendId, nickname) = when (val s = message.sender) {
            Sender.Buddy -> Triple(BUDDY, null, null)
            Sender.Me -> Triple(ME, null, null)
            is Sender.Friend -> Triple(FRIEND, s.friendId, s.nickname)
        }
        return messageDao.insert(
            MessageEntity(
                at = message.at,
                senderType = type,
                friendId = friendId,
                friendNickname = nickname,
                text = message.text,
                copyId = message.copyId,
                sessionId = message.sessionId,
                source = source,
            ),
        )
    }

    override suspend fun deleteAll(source: DataSource) = messageDao.deleteBySource(source)

    private fun MessageEntity.toModel() = BuddyMessage(
        id = id,
        at = at,
        sender = when (senderType) {
            FRIEND -> Sender.Friend(friendId.orEmpty(), friendNickname.orEmpty())
            ME -> Sender.Me
            else -> Sender.Buddy
        },
        text = text,
        copyId = copyId,
        sessionId = sessionId,
    )

    private companion object {
        const val BUDDY = "BUDDY"
        const val FRIEND = "FRIEND"
        const val ME = "ME"
    }
}
