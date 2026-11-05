package com.capstone.pause.core.social.di

import com.capstone.pause.core.social.ComingSoonLeagueRepository
import com.capstone.pause.core.social.DemoFriendRepository
import com.capstone.pause.core.social.FriendRepository
import com.capstone.pause.core.social.LeagueRepository
import com.capstone.pause.core.social.LocalPokeInbox
import com.capstone.pause.core.social.PokeInbox
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 소셜 바인딩(SOCIAL_BACKEND = off: 가상 친구·로컬 기록). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SocialModule {
    @Binds
    abstract fun friendRepository(impl: DemoFriendRepository): FriendRepository

    @Binds
    abstract fun pokeInbox(impl: LocalPokeInbox): PokeInbox

    @Binds
    abstract fun leagueRepository(impl: ComingSoonLeagueRepository): LeagueRepository
}
