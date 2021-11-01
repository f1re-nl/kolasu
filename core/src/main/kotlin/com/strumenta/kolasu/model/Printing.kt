package com.strumenta.kolasu.model

import java.lang.reflect.ParameterizedType
import kotlin.reflect.KVisibility.PUBLIC
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

private const val indentBlock = "  "

fun Node.relevantMemberProperties() = this.javaClass.kotlin.memberProperties
    .filter { !it.name.startsWith("component") && it.name != "position" && it.name != "parent" }

// some fancy reflection tests make sure the cast always succeeds
@Suppress("UNCHECKED_CAST")
fun Node.debugPrint(indent: String = "", skipEmptyCollections: Boolean = false, skipNull: Boolean = false): String {
    val sb = StringBuffer()
    if (this.relevantMemberProperties().isEmpty()) {
        sb.append("$indent${this.javaClass.simpleName}\n")
    } else {
        sb.append("$indent${this.javaClass.simpleName} {\n")
        this.relevantMemberProperties().forEach { property ->
            val mt = property.returnType.javaType
            if (mt is ParameterizedType && mt.rawType == List::class.java) {
                val value = property.get(this) as List<*>
                if (value.isEmpty() && skipEmptyCollections) {
                    // nothing to do
                } else {
                    val paramType = mt.actualTypeArguments[0]
                    if (paramType is Class<*> && Node::class.java.isAssignableFrom(paramType)) {
                        sb.append("$indent$indentBlock${property.name} = [\n")
                        (value as List<Node>).forEach {
                            sb.append(
                                it.debugPrint(
                                    indent + indentBlock + indentBlock, skipEmptyCollections,
                                    skipNull
                                )
                            )
                        }
                        sb.append("$indent$indentBlock]\n")
                    }
                }
            } else {
                if (property.visibility == PUBLIC) {
                    val value = property.get(this)
                    if (value == null && skipNull) {
                        // nothing to do
                    } else {
                        if (value is Node) {
                            sb.append("$indent$indentBlock${property.name} = [\n")
                            sb.append(
                                value.debugPrint(
                                    indent + indentBlock + indentBlock, skipEmptyCollections,
                                    skipNull
                                )
                            )
                            sb.append("$indent$indentBlock]\n")
                        } else {
                            sb.append("$indent$indentBlock${property.name} = ${value}\n")
                        }
                    }
                }
            }
        }
        sb.append("$indent} // ${this.javaClass.simpleName}\n")
    }
    return sb.toString()
}
