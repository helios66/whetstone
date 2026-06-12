package com.deliveryhero.whetstone.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
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
        return emptyList()
    }

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
                    if (hasInjectableMembers) bindings += ScopedProperty(it, injectorProperty(target))
                }
            } else {
                annotationDecl.metaAnnotation(AUTO_INJECTOR_BINDING)?.classArgument(NAME_SCOPE)?.let {
                    if (hasInjectableMembers) bindings += ScopedProperty(it, injectorProperty(target))
                }
                annotationDecl.metaAnnotation(AUTO_INSTANCE_BINDING)?.let { meta ->
                    val base = meta.classArgument(NAME_BASE) ?: return@let
                    val scope = meta.classArgument(NAME_SCOPE) ?: return@let
                    bindings += ScopedProperty(scope, instanceProperty(target, base))
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
    }

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

    private fun classKey(target: TypeName): AnnotationSpec =
        AnnotationSpec.builder(CLASS_KEY).addMember("%T::class", target).build()

    private fun contributesTo(scope: ClassName): AnnotationSpec =
        AnnotationSpec.builder(CONTRIBUTES_TO).addMember("%T::class", scope).build()

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
        const val META_PACKAGE = "com.deliveryhero.whetstone.meta"
        const val AUTO_INJECTOR_BINDING = "$META_PACKAGE.AutoInjectorBinding"
        const val AUTO_INSTANCE_BINDING = "$META_PACKAGE.AutoInstanceBinding"
        const val CONTRIBUTES_INJECTOR = "com.deliveryhero.whetstone.injector.ContributesInjector"
        const val CONTRIBUTES_APP_INJECTOR = "com.deliveryhero.whetstone.app.ContributesAppInjector"

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
        val CLASS_KEY = ClassName(METRO, "ClassKey")
        val CONTRIBUTES_TO = ClassName(METRO, "ContributesTo")
        val MEMBERS_INJECTOR = ClassName(METRO, "MembersInjector")
        val PROVIDES = ClassName(METRO, "Provides")
        val DEPENDENCY_GRAPH = ClassName(METRO, "DependencyGraph")
        val DEPENDENCY_GRAPH_FACTORY = DEPENDENCY_GRAPH.nestedClass("Factory")

        val APPLICATION = ClassName("android.app", "Application")
        val APPLICATION_COMPONENT = ClassName("com.deliveryhero.whetstone.app", "ApplicationComponent")
        val APPLICATION_SCOPE = ClassName("com.deliveryhero.whetstone.app", "ApplicationScope")
    }
}
