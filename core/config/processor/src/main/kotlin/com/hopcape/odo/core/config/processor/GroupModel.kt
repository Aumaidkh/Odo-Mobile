package com.hopcape.odo.core.config.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration

/** Fully qualified names the processor matches on. It never depends on `:core:config`. */
internal object ConfigNames {
    const val PACKAGE = "com.hopcape.odo.core.config"
    const val CONFIG_GROUP = "$PACKAGE.ConfigGroup"
    const val FLAG = "$PACKAGE.Flag"
    const val VALUE = "$PACKAGE.Value"

    /**
     * The processor is a plain JVM module and `:core:config` is a KMP library with no JVM
     * target, so it could not depend on it even if it wanted to. It does not need to: KSP
     * matches annotations by name, and generated code references these types by name too.
     */
    val KEY_FORMAT = Regex("^[a-z][a-z0-9_]*$")
}

/** The types a config property may have. Mirrors `ConfigType` in `:core:config`. */
internal enum class KeyType {
    BOOLEAN, INT, LONG, DOUBLE, STRING, ENUM;

    val isNumber: Boolean get() = this == INT || this == LONG || this == DOUBLE
}

/**
 * One validated key. [defaultLiteral] is already Kotlin source — `true`, `3`, `"OFF"` —
 * because the string written at the declaration site is parsed here, at build time, and
 * never at runtime.
 */
internal data class ParsedKey(
    val propertyName: String,
    val key: String,
    val type: KeyType,
    val defaultLiteral: String,
    val owner: String,
    val why: String,
    val range: String?,
    val enumDeclaration: KSClassDeclaration?,
    val enumConstants: List<String>,
)

internal data class ParsedGroup(
    val declaration: KSClassDeclaration,
    val groupName: String,
    val keys: List<ParsedKey>,
) {
    val simpleName: String get() = declaration.simpleName.asString()
    val packageName: String get() = declaration.packageName.asString()
}
