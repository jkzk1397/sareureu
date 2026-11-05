package com.capstone.pause.core.data.strategy

import android.content.Context
import com.capstone.pause.core.model.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 맥락별 대처 전략 라이브러리(05 문서 6절, `assets/strategies.json`). */
interface StrategyRepository {
    /** 전략 전부. */
    suspend fun strategies(): List<Strategy>
}

/** 자산 파일을 한 번 읽어 둔다. */
@Singleton
internal class AssetStrategyRepository @Inject constructor(@ApplicationContext private val context: Context) :
    StrategyRepository {
    private val mutex = Mutex()
    private var cached: List<Strategy>? = null

    override suspend fun strategies(): List<Strategy> = mutex.withLock {
        cached ?: context.assets.open(ASSET).bufferedReader().use { reader ->
            json.decodeFromString(ListSerializer(Strategy.serializer()), reader.readText())
        }.also { cached = it }
    }

    private companion object {
        const val ASSET = "strategies.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
