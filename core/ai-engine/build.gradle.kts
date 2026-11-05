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

// AI 평가 하네스(05 문서 9절). gemini는 환경변수 GEMINI_API_KEY 필요(없으면 건너뜀).
// 사용: ./gradlew :core:ai-engine:aiEval -Pprovider=mock
tasks.register<JavaExec>("aiEval") {
    group = "verification"
    description = "AI 평가 하네스(05 문서 9절). -Pprovider=mock|gemini"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.capstone.pause.core.ai.engine.eval.AiEvalMainKt")
    val provider = providers.gradleProperty("provider").orElse("mock")
    val outDir = layout.buildDirectory.dir("reports/ai-eval")
    inputs.property("provider", provider)
    outputs.dir(outDir)
    argumentProviders.add(CommandLineArgumentProvider { listOf(provider.get(), outDir.get().asFile.absolutePath) })
}
