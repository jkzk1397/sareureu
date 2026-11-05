plugins {
    alias(libs.plugins.pause.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:insights"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

// 0단계 스텁: CI가 부르므로 먼저 등록한다. 4단계에서 실제 평가로 바꾼다.
// 사용: ./gradlew :core:ai-engine:aiEval -Pprovider=mock
tasks.register<JavaExec>("aiEval") {
    group = "verification"
    description = "AI 평가 하네스 (0단계: 빈 보고서만 쓴다)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.capstone.pause.core.ai.engine.eval.AiEvalMainKt")
    val provider = providers.gradleProperty("provider").orElse("mock")
    val outDir = layout.buildDirectory.dir("reports/ai-eval")
    inputs.property("provider", provider)
    outputs.dir(outDir)
    argumentProviders.add(CommandLineArgumentProvider { listOf(provider.get(), outDir.get().asFile.absolutePath) })
}
