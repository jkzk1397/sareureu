package com.capstone.pause.core.social.di

import com.capstone.pause.core.social.ComingSoonLeagueRepository
import com.capstone.pause.core.social.FriendPresence
import com.capstone.pause.core.social.FriendRepository
import com.capstone.pause.core.social.LeagueRepository
import com.capstone.pause.core.social.PokeInbox
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.remote.FirebaseAnonymousAuth
import com.capstone.pause.core.social.remote.HttpSocialTransport
import com.capstone.pause.core.social.remote.LiveFriendPresence
import com.capstone.pause.core.social.remote.RemoteFriendRepository
import com.capstone.pause.core.social.remote.RemotePokeInbox
import com.capstone.pause.core.social.remote.RemoteSharingRepository
import com.capstone.pause.core.social.remote.SocialAuth
import com.capstone.pause.core.social.remote.SocialServer
import com.capstone.pause.core.social.remote.SocialTransport
import com.capstone.pause.core.social.remote.UploadScheduler
import com.capstone.pause.core.social.remote.WorkUploadScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 소셜 바인딩. 친구 서버 주소가 없거나 시연 모드면 가상 친구, 공유에 동의하면 서버(server/README.md). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SocialModule {
    @Binds
    abstract fun friendRepository(impl: RemoteFriendRepository): FriendRepository

    @Binds
    abstract fun pokeInbox(impl: RemotePokeInbox): PokeInbox

    @Binds
    abstract fun leagueRepository(impl: ComingSoonLeagueRepository): LeagueRepository

    @Binds
    abstract fun socialAuth(impl: FirebaseAnonymousAuth): SocialAuth

    @Binds
    abstract fun socialTransport(impl: HttpSocialTransport): SocialTransport

    @Binds
    abstract fun sharingRepository(impl: RemoteSharingRepository): SharingRepository

    @Binds
    abstract fun uploadScheduler(impl: WorkUploadScheduler): UploadScheduler

    @Binds
    abstract fun friendPresence(impl: LiveFriendPresence): FriendPresence

    /** 서버 주소(빌드 설정). */
    companion object {
        @Provides
        fun socialServer(): SocialServer = SocialServer.FROM_BUILD
    }
}
