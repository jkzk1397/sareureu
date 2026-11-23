# 릴리스 빌드(R8) keep 규칙. 목적은 용량과 속도다(6-B).
# kotlinx.serialization, Hilt, Room, Firebase, WorkManager는 라이브러리가 자기 규칙을 함께 실어 준다.
# 여기에는 우리 코드에서 이름이나 리플렉션에 기대는 것만 적는다. 릴리스 빌드를 깔아 돌려 보고 깨진 것만 더한다.

# kotlinx.serialization: 우리 @Serializable 타입의 serializer()와 Companion을 지킨다(내비 경로 키, 자산 JSON, 저장 형식).
-keepclassmembers @kotlinx.serialization.Serializable class com.capstone.pause.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.capstone.pause.**$$serializer { *; }

# Navigation 3: 백 스택을 저장·복원할 때 경로 키를 이름으로 찾는다.
-keep class * implements androidx.navigation3.runtime.NavKey { *; }

# WorkManager + Hilt: 예약 작업은 클래스 이름으로 다시 만들어진다.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# enum 이름은 저장값·서버 값(연령대·신분·맥락)으로 쓰인다: valueOf/name이 바뀌면 안 된다.
-keepclassmembers enum com.capstone.pause.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# zxing: QR 부호화(core)만 쓴다. 리플렉션 없음 — 따로 지킬 것 없음.

# 오류 보고에 줄 번호를 남긴다(이름은 줄여도).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
