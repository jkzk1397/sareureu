package com.capstone.pause.core.database.di

import android.content.Context
import androidx.room.Room
import com.capstone.pause.core.database.PauseDatabase
import com.capstone.pause.core.database.dao.AiCallDao
import com.capstone.pause.core.database.dao.CopyPackDao
import com.capstone.pause.core.database.dao.FriendDao
import com.capstone.pause.core.database.dao.MessageDao
import com.capstone.pause.core.database.dao.NudgeEventDao
import com.capstone.pause.core.database.dao.PlanDao
import com.capstone.pause.core.database.dao.PokeDao
import com.capstone.pause.core.database.dao.ReportDao
import com.capstone.pause.core.database.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** DB와 DAO 제공. 파괴적 마이그레이션은 쓰지 않는다. */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PauseDatabase =
        Room.databaseBuilder(context, PauseDatabase::class.java, PauseDatabase.NAME).build()

    @Provides
    fun sessionDao(db: PauseDatabase): SessionDao = db.sessionDao()

    @Provides
    fun nudgeEventDao(db: PauseDatabase): NudgeEventDao = db.nudgeEventDao()

    @Provides
    fun messageDao(db: PauseDatabase): MessageDao = db.messageDao()

    @Provides
    fun planDao(db: PauseDatabase): PlanDao = db.planDao()

    @Provides
    fun pokeDao(db: PauseDatabase): PokeDao = db.pokeDao()

    @Provides
    fun reportDao(db: PauseDatabase): ReportDao = db.reportDao()

    @Provides
    fun copyPackDao(db: PauseDatabase): CopyPackDao = db.copyPackDao()

    @Provides
    fun aiCallDao(db: PauseDatabase): AiCallDao = db.aiCallDao()

    @Provides
    fun friendDao(db: PauseDatabase): FriendDao = db.friendDao()
}
