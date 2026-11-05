package com.capstone.pause.core.ai.client

import com.capstone.pause.core.ai.engine.llm.ToolDeclaration
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `core:ai-engine`의 도구 선언(JSON 스키마)을 Firebase AI `FunctionDeclaration`으로 바꾼다.
 * 도구 인자는 문자열·열거·정수·숫자·불리언·배열·객체만 쓰므로 그 범위만 옮긴다.
 */
internal object ToolSchemas {
    /** 선언 하나 변환. `required`에 없는 속성은 선택 인자. */
    fun toFirebase(decl: ToolDeclaration): FunctionDeclaration {
        val props = decl.parametersSchema["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val required = decl.parametersSchema["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()
        val parameters = props.mapValues { (_, v) -> schemaOf(v.jsonObject) }
        return FunctionDeclaration(
            decl.name,
            decl.description,
            parameters,
            optionalParameters = parameters.keys.filter { it !in required },
        )
    }

    /** JSON 스키마 한 덩어리 → [Schema]. 모르는 형은 문자열로 둔다. */
    fun schemaOf(node: JsonObject): Schema {
        val description = node["description"]?.jsonPrimitive?.contentOrNull
        val enumValues = (node["enum"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
        if (enumValues != null) return Schema.enumeration(enumValues, description)
        return when (node["type"]?.jsonPrimitive?.contentOrNull) {
            "integer" -> Schema.integer(description)

            "number" -> Schema.double(description)

            "boolean" -> Schema.boolean(description)

            "array" -> Schema.array(schemaOf(node["items"]?.jsonObject ?: JsonObject(emptyMap())), description)

            "object" -> {
                val props = node["properties"]?.jsonObject ?: JsonObject(emptyMap())
                val required = node["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet()
                    .orEmpty()
                val mapped = props.mapValues { (_, v) -> schemaOf(v.jsonObject) }
                Schema.obj(mapped, mapped.keys.filter { it !in required }, description)
            }

            else -> Schema.string(description)
        }
    }
}
