package com.unpopulardev.whetstone.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Translates Whetstone's annotations into [Metro](https://github.com/zacsweers/metro) contributions.
 *
 * For every class annotated with a Whetstone annotation it generates a `@ContributesTo`
 * contributing interface that binds the class (or its `MembersInjector`) into the relevant
 * multibinding map. KSP runs before Metro's compiler plugin, so Metro merges these contributions
 * into the application/extension graphs automatically.
 *
 * Triggers are discovered structurally:
 * - any annotation meta-annotated with `@AutoInjectorBinding(scope)` produces a member-injector
 *   binding (`MembersInjector<T>` into `Map<KClass<*>, MembersInjector<*>>`);
 * - any annotation meta-annotated with `@AutoInstanceBinding(base, scope)` produces an instance
 *   binding (`T` into `Map<KClass<*>, base>`);
 * - the explicit `@ContributesInjector(scope)` produces a member-injector binding;
 * - `@ContributesAppInjector(generateAppComponent = true)` additionally generates the root
 *   `GeneratedApplicationComponent` `@DependencyGraph`.
 */
internal class WhetstoneSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var invoked = false

    /** Discovered contributions for this module, emitted as a JSON graph fragment (see spec). */
    private val graphRecords = mutableListOf<GraphRecord>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // We scan every class in the round (rather than a fixed annotation set, since triggers are
        // open-ended meta-annotations), so run exactly once to avoid re-emitting generated files in
        // subsequent KSP rounds.
        if (invoked) return emptyList()
        invoked = true

        resolver.getAllFiles().forEach { file ->
            file.declarations
                .filterIsInstance<KSClassDeclaration>()
                .forEach(::processClass)
        }
        writeGraphFragment()
        return emptyList()
    }

    /** One contributed class: its fq name, target scope, kind, and (for instance bindings) base. */
    private class GraphRecord(
        val fqName: String,
        val scope: String,
        val kind: String,
        val base: String?,
    )

    /** A single `@Binds @IntoMap` declaration plus the scope its contributing interface targets. */
    private class ScopedProperty(val scope: ClassName, val property: PropertySpec)

    private fun processClass(clazz: KSClassDeclaration) {
        if (clazz.classKind == ClassKind.ANNOTATION_CLASS) return

        val target = clazz.toClassName()
        // Accumulate every binding the class triggers, then emit a SINGLE file for the class. A class
        // may carry more than one trigger (e.g. an explicit @ContributesInjector plus a meta-trigger);
        // emitting one file per trigger would collide on the file name.
        val bindings = mutableListOf<ScopedProperty>()
        var generateAppGraph = false
        // A member-injector binding (MembersInjector<T>) is only emitted when T actually has
        // @Inject members. Metro (unlike Dagger) does not synthesise a no-op MembersInjector for a
        // class with nothing to inject, so emitting the binding for such a class would fail to
        // compile. Computed lazily — instance-only triggers (ViewModel/Fragment/Worker) don't need it.
        val hasInjectableMembers by lazy { clazz.hasInjectableMembers() }

        for (annotation in clazz.annotations) {
            val annotationDecl = annotation.annotationType.resolve().declaration as? KSClassDeclaration
                ?: continue
            val annotationFqName = annotationDecl.qualifiedName?.asString() ?: continue

            if (annotationFqName == CONTRIBUTES_INJECTOR) {
                annotation.classArgument(NAME_SCOPE)?.let {
                    if (hasInjectableMembers) {
                        bindings += ScopedProperty(it, injectorProperty(target))
                        recordGraph(target, it, kind = "injector", base = null)
                    }
                }
            } else {
                annotationDecl.metaAnnotation(AUTO_INJECTOR_BINDING)?.classArgument(NAME_SCOPE)?.let {
                    if (hasInjectableMembers) {
                        bindings += ScopedProperty(it, injectorProperty(target))
                        recordGraph(target, it, kind = "injector", base = null)
                    }
                }
                annotationDecl.metaAnnotation(AUTO_INSTANCE_BINDING)?.let { meta ->
                    val base = meta.classArgument(NAME_BASE) ?: return@let
                    val scope = meta.classArgument(NAME_SCOPE) ?: return@let
                    bindings += ScopedProperty(scope, instanceProperty(target, base))
                    recordGraph(target, scope, kind = "instance", base = base.simpleName)
                }
            }

            if (annotationFqName == CONTRIBUTES_APP_INJECTOR &&
                annotation.booleanArgument(NAME_GENERATE_APP_COMPONENT)
            ) {
                generateAppGraph = true
            }
        }

        if (bindings.isNotEmpty()) writeModules(clazz, target, bindings)
        if (generateAppGraph) generateApplicationGraph(clazz)

        processInjectorContributions(clazz, target)
    }

    /**
     * Translates the Anvil-compatible `injector.*` contribution annotations into native Metro
     * `@ContributesTo` modules (which DO emit cross-module hints, unlike Metro's annotation interop).
     * One `<Class>_WhetstoneContribution` file per annotated class:
     *
     * - `@ContributesTo(scope, replaces)` on an interface M -> `@ContributesTo interface …Contribution : M`
     *   (interface aggregation — pulls M's `@Binds`/`@Provides`/accessors into the scope).
     * - `@ContributesBinding(scope, boundType, replaces)` -> a `@Binds val Impl.bind: BoundType`.
     * - `@ContributesMultibinding(scope, boundType, replaces)` -> `@Binds @IntoSet` (set) or, when the
     *   class carries a map-key annotation, `@Binds @IntoMap @<MapKey>` (map) — Anvil convention.
     *
     * `replaces = [X::class]` is mapped to X's generated `…_WhetstoneContribution` module, so swapping
     * a production binding/module for a test/fake one works exactly as in Anvil.
     */
    private fun processInjectorContributions(clazz: KSClassDeclaration, target: ClassName) {
        for (annotation in clazz.annotations) {
            val fqName = annotation.annotationType.resolve().declaration.qualifiedName?.asString() ?: continue
            val scope = annotation.classArgument(NAME_SCOPE) ?: continue
            val replaces = annotation.classListArgument(NAME_REPLACES).map { it.contributionModule() }

            val contribution = when (fqName) {
                CONTRIBUTES_TO_FQ ->
                    TypeSpec.interfaceBuilder(target.contributionName())
                        .addSuperinterface(target)
                        .addAnnotation(contributesTo(scope, replaces))
                        .build()

                CONTRIBUTES_BINDING_FQ ->
                    bindingModule(clazz, target, scope, replaces, multibinding = false)

                CONTRIBUTES_MULTIBINDING_FQ ->
                    bindingModule(clazz, target, scope, replaces, multibinding = true)

                else -> continue
            }
            writeFile(clazz, target.packageName, target.contributionSimpleName(), contribution)
        }
    }

    /** A `@ContributesTo` interface holding the single `@Binds [@IntoSet|@IntoMap @MapKey]` for [target]. */
    private fun bindingModule(
        clazz: KSClassDeclaration,
        target: ClassName,
        scope: ClassName,
        replaces: List<ClassName>,
        multibinding: Boolean,
    ): TypeSpec {
        val boundType = (clazz.boundTypeArgument() ?: clazz.singleSupertype())
        val mapKey = if (multibinding) clazz.mapKeyAnnotation()?.toAnnotationSpec() else null
        val binds = PropertySpec
            .builder("bind${target.simpleName}", boundType)
            .receiver(target)
            .addAnnotation(BINDS)
            .apply {
                when {
                    mapKey != null -> { addAnnotation(INTO_MAP); addAnnotation(mapKey) }
                    multibinding -> addAnnotation(INTO_SET)
                }
            }
            .build()
        return TypeSpec.interfaceBuilder(target.contributionName())
            .addAnnotation(contributesTo(scope, replaces))
            .addProperty(binds)
            .build()
    }

    private fun ClassName.contributionSimpleName(): String =
        "${simpleNames.joinToString("_")}_WhetstoneContribution"

    private fun ClassName.contributionName(): ClassName =
        ClassName(packageName, contributionSimpleName())

    /** The generated contribution-module name for a class referenced in `replaces`. */
    private fun ClassName.contributionModule(): ClassName = contributionName()

    /**
     * ```
     * @Binds @IntoMap @ClassKey(Foo::class)
     * val MembersInjector<Foo>.bindFooInjector: MembersInjector<*>
     * ```
     */
    private fun injectorProperty(target: ClassName): PropertySpec = PropertySpec
        .builder("bind${target.simpleName}Injector", MEMBERS_INJECTOR.parameterizedBy(STAR))
        .receiver(MEMBERS_INJECTOR.parameterizedBy(target))
        .addAnnotation(BINDS)
        .addAnnotation(INTO_MAP)
        .addAnnotation(classKey(target))
        .build()

    /**
     * ```
     * @Binds @IntoMap @ClassKey(Foo::class)
     * val Foo.bindFoo: Base
     * ```
     */
    private fun instanceProperty(target: ClassName, base: ClassName): PropertySpec = PropertySpec
        .builder("bind${target.simpleName}", base)
        .receiver(target)
        .addAnnotation(BINDS)
        .addAnnotation(INTO_MAP)
        .addAnnotation(classKey(target))
        .build()

    /**
     * Emits one file per annotated class containing a `@ContributesTo(scope)` interface per distinct
     * scope, each holding that scope's `@Binds @IntoMap` declarations. For example:
     * ```
     * @ContributesTo(ViewModelScope::class)
     * interface Foo_WhetstoneModule {
     *   @Binds @IntoMap @ClassKey(Foo::class)
     *   val Foo.bindFoo: ViewModel
     * }
     * ```
     */
    private fun writeModules(
        clazz: KSClassDeclaration,
        target: ClassName,
        bindings: List<ScopedProperty>,
    ) {
        val fileName = "${target.simpleNames.joinToString("_")}_WhetstoneModule"
        val byScope = bindings.groupBy { it.scope }
        val singleScope = byScope.size == 1

        val fileBuilder = FileSpec.builder(target.packageName, fileName)
            .addFileComment("Automatically generated by Whetstone. DO NOT MODIFY")

        byScope.forEach { (scope, scopedProperties) ->
            val interfaceName = if (singleScope) fileName else "${fileName}_${scope.simpleName}"
            val moduleBuilder = TypeSpec.interfaceBuilder(interfaceName)
                .addAnnotation(contributesTo(scope))
            scopedProperties
                .distinctBy { it.property.name }
                .forEach { moduleBuilder.addProperty(it.property) }
            fileBuilder.addType(moduleBuilder.build())
        }

        fileBuilder.build().writeTo(
            codeGenerator = codeGenerator,
            aggregating = false,
            originatingKSFiles = listOfNotNull(clazz.containingFile),
        )
    }

    /**
     * ```
     * @DependencyGraph(scope = ApplicationScope::class)
     * interface GeneratedApplicationComponent : ApplicationComponent {
     *   @DependencyGraph.Factory
     *   fun interface Factory {
     *     fun create(@Provides application: Application): GeneratedApplicationComponent
     *   }
     *   companion object {
     *     fun create(application: Application): ApplicationComponent =
     *       createGraphFactory<Factory>().create(application)
     *   }
     * }
     * ```
     */
    private fun generateApplicationGraph(clazz: KSClassDeclaration) {
        val packageName = clazz.packageName.asString()
        val graph = ClassName(packageName, GENERATED_APP_COMPONENT)
        val factory = graph.nestedClass("Factory")

        val createInFactory = FunSpec.builder("create")
            .addModifiers(KModifier.ABSTRACT)
            .addParameter(
                ParameterSpec.builder("application", APPLICATION)
                    .addAnnotation(PROVIDES)
                    .build()
            )
            .returns(graph)
            .build()

        val factorySpec = TypeSpec.funInterfaceBuilder(factory)
            .addAnnotation(DEPENDENCY_GRAPH_FACTORY)
            .addFunction(createInFactory)
            .build()

        // Do NOT declare a companion object: Metro generates the graph's companion and the
        // `create` factory SAM into it automatically (it only does so when none is present). That
        // is what makes `GeneratedApplicationComponent.create(application)` resolve.
        val graphSpec = TypeSpec.interfaceBuilder(graph)
            .addSuperinterface(APPLICATION_COMPONENT)
            .addAnnotation(
                AnnotationSpec.builder(DEPENDENCY_GRAPH)
                    .addMember("scope = %T::class", APPLICATION_SCOPE)
                    .build()
            )
            .addType(factorySpec)
            .build()

        writeFile(clazz, packageName, GENERATED_APP_COMPONENT, graphSpec)
    }

    private fun writeFile(
        clazz: KSClassDeclaration,
        packageName: String,
        fileName: String,
        type: TypeSpec,
    ) {
        val fileSpec = FileSpec.builder(packageName, fileName)
            .addFileComment("Automatically generated by Whetstone. DO NOT MODIFY")
            .addType(type)
            .build()
        val originating = clazz.containingFile
        fileSpec.writeTo(
            codeGenerator = codeGenerator,
            aggregating = false,
            originatingKSFiles = listOfNotNull(originating),
        )
    }

    private fun recordGraph(target: ClassName, scope: ClassName, kind: String, base: String?) {
        graphRecords += GraphRecord(target.canonicalName, scope.simpleName, kind, base)
    }

    /**
     * Emits a per-module JSON graph fragment describing the contributions discovered in this module,
     * consumed by the `whetstoneDepGraph` Gradle task to render a Mermaid diagram. Lands at
     * `build/generated/ksp/<variant>/resources/whetstone/graph/whetstone-dep-graph.json`.
     */
    private fun writeGraphFragment() {
        if (graphRecords.isEmpty()) return
        val json = buildString {
            append("[\n")
            graphRecords.forEachIndexed { index, record ->
                append("  {\"fqName\":\"").append(record.fqName)
                    .append("\",\"scope\":\"").append(record.scope)
                    .append("\",\"kind\":\"").append(record.kind).append('"')
                if (record.base != null) append(",\"base\":\"").append(record.base).append('"')
                append('}')
                if (index < graphRecords.lastIndex) append(',')
                append('\n')
            }
            append("]\n")
        }
        codeGenerator.createNewFileByPath(
            dependencies = Dependencies.ALL_FILES,
            path = "whetstone/graph/whetstone-dep-graph",
            extensionName = "json",
        ).use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    private fun classKey(target: TypeName): AnnotationSpec =
        AnnotationSpec.builder(CLASS_KEY).addMember("%T::class", target).build()

    private fun contributesTo(scope: ClassName, replaces: List<ClassName> = emptyList()): AnnotationSpec {
        val builder = AnnotationSpec.builder(CONTRIBUTES_TO).addMember("%T::class", scope)
        if (replaces.isNotEmpty()) {
            val joined = replaces.map { CodeBlock.of("%T::class", it) }.joinToCode(", ")
            builder.addMember("replaces = [%L]", joined)
        }
        return builder.build()
    }

    private fun KSAnnotation.classListArgument(name: String): List<ClassName> {
        val value = arguments.firstOrNull { it.name?.asString() == name }?.value as? List<*> ?: return emptyList()
        return value.mapNotNull { (it as? KSType)?.toClassName() }
    }

    /** The `boundType` of the class's contribution annotation, or null when it is `Unit` (= infer). */
    private fun KSClassDeclaration.boundTypeArgument(): ClassName? {
        val annotation = annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() in INJECTOR_CONTRIBUTION_FQ
        } ?: return null
        return annotation.classArgument(NAME_BOUND_TYPE)?.takeUnless { it.canonicalName == UNIT_FQ }
    }

    /** The single non-`Any` supertype, used when `boundType` is left as `Unit`. */
    private fun KSClassDeclaration.singleSupertype(): ClassName = superTypes
        .mapNotNull { it.resolve().declaration as? KSClassDeclaration }
        .filter { it.qualifiedName?.asString() != ANY_FQ }
        .toList()
        .single()
        .toClassName()

    /** The map-key annotation on the class (one itself meta-annotated with Metro/Dagger `@MapKey`), if any. */
    private fun KSClassDeclaration.mapKeyAnnotation(): KSAnnotation? = annotations.firstOrNull { annotation ->
        val declaration = annotation.annotationType.resolve().declaration as? KSClassDeclaration
        declaration?.annotations?.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() in MAP_KEY_FQ_NAMES
        } == true
    }

    private fun KSClassDeclaration.metaAnnotation(fqName: String): KSAnnotation? =
        annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == fqName
        }

    private fun KSAnnotation.classArgument(name: String): ClassName? {
        val value = arguments.firstOrNull { it.name?.asString() == name }?.value as? KSType
        return value?.toClassName()
    }

    private fun KSAnnotation.booleanArgument(name: String): Boolean =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? Boolean ?: false

    /** True if the class — or any of its supertypes — declares an `@Inject` property or function. */
    private fun KSClassDeclaration.hasInjectableMembers(): Boolean {
        if (declaresInjectMember()) return true
        return superTypes.any { superType ->
            (superType.resolve().declaration as? KSClassDeclaration)?.hasInjectableMembers() == true
        }
    }

    private fun KSClassDeclaration.declaresInjectMember(): Boolean =
        getDeclaredProperties().any { it.isInjectAnnotated() } ||
            getDeclaredFunctions().any { it.isInjectAnnotated() }

    private fun KSAnnotated.isInjectAnnotated(): Boolean =
        annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() in INJECT_FQ_NAMES
        }

    private companion object {
        const val META_PACKAGE = "com.unpopulardev.whetstone.meta"
        const val AUTO_INJECTOR_BINDING = "$META_PACKAGE.AutoInjectorBinding"
        const val AUTO_INSTANCE_BINDING = "$META_PACKAGE.AutoInstanceBinding"
        const val CONTRIBUTES_INJECTOR = "com.unpopulardev.whetstone.injector.ContributesInjector"
        const val CONTRIBUTES_APP_INJECTOR = "com.unpopulardev.whetstone.app.ContributesAppInjector"

        const val INJECTOR_PACKAGE = "com.unpopulardev.whetstone.injector"
        const val CONTRIBUTES_TO_FQ = "$INJECTOR_PACKAGE.ContributesTo"
        const val CONTRIBUTES_BINDING_FQ = "$INJECTOR_PACKAGE.ContributesBinding"
        const val CONTRIBUTES_MULTIBINDING_FQ = "$INJECTOR_PACKAGE.ContributesMultibinding"
        val INJECTOR_CONTRIBUTION_FQ = setOf(CONTRIBUTES_BINDING_FQ, CONTRIBUTES_MULTIBINDING_FQ)
        val MAP_KEY_FQ_NAMES = setOf("dev.zacsweers.metro.MapKey", "dagger.MapKey")

        const val NAME_REPLACES = "replaces"
        const val NAME_BOUND_TYPE = "boundType"
        const val UNIT_FQ = "kotlin.Unit"
        const val ANY_FQ = "kotlin.Any"

        val INJECT_FQ_NAMES = setOf(
            "javax.inject.Inject",
            "jakarta.inject.Inject",
            "dev.zacsweers.metro.Inject",
        )

        const val NAME_SCOPE = "scope"
        const val NAME_BASE = "base"
        const val NAME_GENERATE_APP_COMPONENT = "generateAppComponent"

        const val GENERATED_APP_COMPONENT = "GeneratedApplicationComponent"

        val METRO = "dev.zacsweers.metro"
        val BINDS = ClassName(METRO, "Binds")
        val INTO_MAP = ClassName(METRO, "IntoMap")
        val INTO_SET = ClassName(METRO, "IntoSet")
        val CLASS_KEY = ClassName(METRO, "ClassKey")
        val CONTRIBUTES_TO = ClassName(METRO, "ContributesTo")
        val MEMBERS_INJECTOR = ClassName(METRO, "MembersInjector")
        val PROVIDES = ClassName(METRO, "Provides")
        val DEPENDENCY_GRAPH = ClassName(METRO, "DependencyGraph")
        val DEPENDENCY_GRAPH_FACTORY = DEPENDENCY_GRAPH.nestedClass("Factory")

        val APPLICATION = ClassName("android.app", "Application")
        val APPLICATION_COMPONENT = ClassName("com.unpopulardev.whetstone.app", "ApplicationComponent")
        val APPLICATION_SCOPE = ClassName("com.unpopulardev.whetstone.app", "ApplicationScope")
    }
}
