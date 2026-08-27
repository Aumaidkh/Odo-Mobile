package com.hopcape.odo.core.config.processor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration

class ConfigProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ConfigProcessor(environment.codeGenerator, environment.logger)
}

/**
 * Reads every `@ConfigGroup` interface in the module being compiled and generates its
 * implementation, its flows, its registry contribution and its Koin module.
 *
 * Every rejection below fails the build with a message naming the offending property. The
 * point is that a bad declaration is impossible to ship, not that it is caught early: a
 * key whose default does not match its type would otherwise resolve to something nobody
 * declared, on a device, with no way to tell.
 *
 * KSP sees one module at a time, so a key declared by two different modules is not
 * visible here. That is caught when the registry is assembled — see `ConfigRegistry`.
 */
class ConfigProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Everything needed is resolvable in the first round; nothing is deferred.
        if (processed) return emptyList()
        processed = true

        val groups = resolver.getSymbolsWithAnnotation(ConfigNames.CONFIG_GROUP)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
            .mapNotNull(::parseGroup)

        if (groups.isEmpty()) return emptyList()
        if (!keysAreUniqueInThisModule(groups)) return emptyList()

        val codegen = ConfigCodegen(codeGenerator)
        groups.forEach(codegen::generate)
        return emptyList()
    }

    // ── Parsing and validation ────────────────────────────────────────────────

    private fun parseGroup(declaration: KSClassDeclaration): ParsedGroup? {
        if (declaration.classKind != ClassKind.INTERFACE) {
            logger.error("@ConfigGroup can only be applied to an interface", declaration)
            return null
        }
        val annotation = declaration.annotationOf(ConfigNames.CONFIG_GROUP) ?: return null
        val groupName = annotation.argument<String>("name").orEmpty()
        if (groupName.isBlank()) {
            logger.error("@ConfigGroup needs a name", declaration)
            return null
        }

        val properties = declaration.getDeclaredProperties().toList()
        if (properties.isEmpty()) {
            logger.error("@ConfigGroup '$groupName' declares no keys", declaration)
            return null
        }

        val keys = properties.map { parseKey(it) }
        if (keys.any { it == null }) return null

        return ParsedGroup(declaration, groupName, keys.filterNotNull())
    }

    private fun parseKey(property: KSPropertyDeclaration): ParsedKey? {
        val name = property.simpleName.asString()
        val flag = property.annotationOf(ConfigNames.FLAG)
        val value = property.annotationOf(ConfigNames.VALUE)
        if (flag == null && value == null) {
            logger.error("'$name' has neither @Flag nor @Value", property)
            return null
        }
        if (flag != null && value != null) {
            logger.error("'$name' has both @Flag and @Value", property)
            return null
        }

        val resolvedType = property.type.resolve()
        if (resolvedType.isMarkedNullable) {
            // There is always a compiled default, so a config value is never absent.
            logger.error("'$name' is nullable; a config key always has a value", property)
            return null
        }
        val type = keyTypeOf(property) ?: run {
            logger.error(
                "'$name' has an unsupported type. Use Boolean, Int, Long, Double, String, " +
                    "or a string-backed enum",
                property,
            )
            return null
        }
        val enumDeclaration = type.second
        val enumConstants = enumDeclaration?.enumConstantNames().orEmpty()

        val annotation = flag ?: value!!
        val key = annotation.argument<String>("key").orEmpty()
        if (!ConfigNames.KEY_FORMAT.matches(key)) {
            logger.error("'$name' has key '$key'; keys must match ${ConfigNames.KEY_FORMAT.pattern}", property)
            return null
        }
        val owner = annotation.argument<String>("owner").orEmpty()
        val why = annotation.argument<String>("why").orEmpty()
        if (owner.isBlank() || why.isBlank()) {
            logger.error("'$name' needs both an owner and a why", property)
            return null
        }

        if (flag != null && type.first != KeyType.BOOLEAN) {
            logger.error("'$name' uses @Flag but is not a Boolean; use @Value", property)
            return null
        }
        if (value != null && type.first == KeyType.BOOLEAN) {
            logger.error("'$name' is a Boolean; use @Flag", property)
            return null
        }

        val rawDefault = if (flag != null) {
            annotation.argument<Boolean>("default").toString()
        } else {
            annotation.argument<String>("default").orEmpty()
        }
        val literal = Defaults.literal(rawDefault, type.first, enumConstants) ?: run {
            val expected = if (type.first == KeyType.ENUM) {
                "one of ${enumConstants.joinToString()}"
            } else {
                "a ${type.first.name.lowercase()}"
            }
            logger.error("'$name' has default \"$rawDefault\", which is not $expected", property)
            return null
        }

        val range = annotation.argument<String>("range").orEmpty()
        if (range.isNotBlank()) {
            if (!type.first.isNumber) {
                logger.error("'$name' declares a range but is not a number", property)
                return null
            }
            if (!Defaults.rangeParses(range)) {
                logger.error("'$name' has range \"$range\", which is not a valid \"min..max\"", property)
                return null
            }
            if (!Defaults.rangeContains(range, rawDefault)) {
                logger.error("'$name' has default \"$rawDefault\", outside its range \"$range\"", property)
                return null
            }
        }

        return ParsedKey(
            propertyName = name,
            key = key,
            type = type.first,
            defaultLiteral = literal,
            owner = owner,
            why = why,
            range = range.takeIf { it.isNotBlank() },
            enumDeclaration = enumDeclaration,
            enumConstants = enumConstants,
        )
    }

    private fun keysAreUniqueInThisModule(groups: List<ParsedGroup>): Boolean {
        val seen = mutableMapOf<String, ParsedGroup>()
        var unique = true
        for (group in groups) {
            for (key in group.keys) {
                val previous = seen.put(key.key, group)
                if (previous != null) {
                    logger.error(
                        "Key '${key.key}' is declared twice, in ${previous.simpleName} and " +
                            "${group.simpleName}",
                        group.declaration,
                    )
                    unique = false
                }
            }
        }
        return unique
    }

    // ── KSP helpers ───────────────────────────────────────────────────────────

    private fun keyTypeOf(property: KSPropertyDeclaration): Pair<KeyType, KSClassDeclaration?>? {
        val declaration = property.type.resolve().declaration
        return when (declaration.qualifiedName?.asString()) {
            "kotlin.Boolean" -> KeyType.BOOLEAN to null
            "kotlin.Int" -> KeyType.INT to null
            "kotlin.Long" -> KeyType.LONG to null
            "kotlin.Double" -> KeyType.DOUBLE to null
            "kotlin.String" -> KeyType.STRING to null
            else -> (declaration as? KSClassDeclaration)
                ?.takeIf { it.classKind == ClassKind.ENUM_CLASS }
                ?.let { KeyType.ENUM to it }
        }
    }
}

private fun KSAnnotated.annotationOf(qualifiedName: String): KSAnnotation? =
    annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
    }

/**
 * Reads an annotation argument, falling back to the declared default. KSP only lists an
 * argument in [KSAnnotation.arguments] when the source wrote it, so an omitted `range`
 * would otherwise read as absent rather than as its `""` default.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> KSAnnotation.argument(name: String): T? =
    (arguments.firstOrNull { it.name?.asString() == name }
        ?: defaultArguments.firstOrNull { it.name?.asString() == name })
        ?.value as? T

private fun KSClassDeclaration.enumConstantNames(): List<String> =
    declarations.filterIsInstance<KSClassDeclaration>()
        .filter { it.classKind == ClassKind.ENUM_ENTRY }
        .map { it.simpleName.asString() }
        .toList()
