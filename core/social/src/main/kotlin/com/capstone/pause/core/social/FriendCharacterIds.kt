package com.capstone.pause.core.social

/**
 * 친구 프로필 캐릭터 번호 정하기(순수). 친구의 공개 ID(서버 친구)나 이름(가상 친구)을 해시해 0~7 중 하나로 정한다.
 * 서버에 따로 저장하지 않고 공개 ID만으로 정하므로, 두 폰에서 같은 친구가 같은 캐릭터로 보인다.
 * 그림은 `core:designsystem`의 `FriendAvatar(characterId, size)`가 그린다. 친구끼리 같은 캐릭터가 겹칠 수 있다.
 */
object FriendCharacterIds {
    /** 캐릭터 수(`core:designsystem`의 `FriendCharacter.COUNT`와 같다). */
    const val COUNT = 8

    /**
     * [key]로 정한 번호(0~7). 같은 글자면 어느 폰·어느 버전에서나 같은 번호다
     * (FNV-1a 32비트를 UTF-8 바이트에 직접 계산한다. 플랫폼의 hashCode에 기대지 않는다).
     */
    fun of(key: String): Int {
        var hash = FNV_OFFSET
        for (byte in key.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toInt() and BYTE_MASK)
            hash *= FNV_PRIME
        }
        return Math.floorMod(hash, COUNT)
    }

    /** 친구 한 명의 번호: 서버 친구는 공개 ID로, 가상 친구([demo])는 이름으로. */
    fun of(friend: Standing, demo: Boolean): Int = of(if (demo) friend.nickname else friend.id)

    private const val FNV_OFFSET = -0x7ee3623b // 0x811C9DC5
    private const val FNV_PRIME = 0x01000193
    private const val BYTE_MASK = 0xFF
}
