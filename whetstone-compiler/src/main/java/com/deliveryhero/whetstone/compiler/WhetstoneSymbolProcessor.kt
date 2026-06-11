package com.deliveryhero.whetstone.compiler

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
import com.squareup.kotlinpoet.MemberName
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

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getAllFiles().forEach { file ->
            file.declarations
                .filterIsInstance<KSClassDeclaration>()
                .forEach(::processClass)
        }
        return emptyList()
    }

    private fun processClass(clazz: KSClassDeclaration) {
        if (clazz.classKind == ClassKind.ANNOTATION_CLASS) return

        var bindingGenerated = false
        for (annotation in clazz.annotations) {
            val annotationDecl = annotation.annotationType.resolve().declaration as? KSClassDeclaration
                ?: continue
            val annotationFqName = annotationDecl.qualifiedName?.asString() ?: continue

            when {
                annotationFqName == CONTRIBUTES_INJECTOR -> {
                    val scope = annotation.classArgument(NAME_SCOPE) ?: continue
                    generateInjectorModule(clazz, scope)
                    bindingGenerated = true
                }

                else -> {
                    annotationDecl.metaAnnotation(AUTO_INJECTOR_BINDING)?.let { meta ->
                        val scope = meta.classArgument(NAME_SCOPE) ?: return@let
                        generateInjectorModule(clazz, scope)
                        bindingGenerated = true
                    }
                    annotationDecl.metaAnnotation(AUTO_INSTANCE_BINDING)?.let { meta ->
                        val base = meta.classArgument(NAME_BASE) ?: return@let
                        val scope = meta.classArgument(NAME_SCOPE) ?: return@let
                        generateInstanceModule(clazz, base, scope)
                        bindingGenerated = true
                    }
                }
            }

            if (annotationFqName == CONTRIBUTES_APP_INJECTOR &&
                annotation.booleanArgument(NAME_GENERATE_APP_COMPONENT)
            ) {
                generateApplicationGraph(clazz)
            }
        }

        // Defensive: a missing binding here usually means a malformed custom annotation.
        if (!bindingGenerated) return
    }

    /**
     * ```
     * @ContributesTo(scope::class)
     * interface Foo_WhetstoneModule {
     *   @Binds @IntoMap @ClassKey(Foo::class)
     *   val MembersInjector<Foo>.bindFooInjector: MembersInjector<*>
     * }
     * ```
     */
    private fun generateInjectorModule(clazz: KSClassDeclaration, scope: ClassName) {
        val target = clazz.toClassName()
        val property = PropertySpec
            .builder("bind${target.simpleName}Injector", MEMBERS_INJECTOR.parameterizedBy(STAR))
            .receiver(MEMBERS_INJECTOR.parameterizedBy(target))
            .addAnnotation(BINDS)
            .addAnnotation(INTO_MAP)
            .addAnnotation(classKey(target))
            .build()
        writeModule(clazz, target, scope, property)
    }

    /**
     * ```
     * @ContributesTo(scope::class)
     * interface Foo_WhetstoneModule {
     *   @Binds @IntoMap @ClassKey(Foo::class)
     *   val Foo.bindFoo: Base
     * }
     * ```
     */
    private fun generateInstanceModule(clazz: KSClassDeclaration, base: ClassName, scope: ClassName) {
        val target = clazz.toClassName()
        val property = PropertySpec
            .builder("bind${target.simpleName}", base)
            .receiver(target)
            .addAnnotation(BINDS)
            .addAnnotation(INTO_MAP)
            .addAnnotation(classKey(target))
            .build()
        writeModule(clazz, target, scope, property)
    }

    private fun writeModule(
        clazz: KSClassDeclaration,
        target: ClassName,
        scope: ClassName,
        property: PropertySpec,
    ) {
        val moduleName = "${target.simpleNames.joinToString("_")}_WhetstoneModule"
        val module = TypeSpec.interfaceBuilder(moduleName)
            .addAnnotation(contributesTo(scope))
            .addProperty(property)
            .build()
        writeFile(clazz, target.packageName, moduleName, module)
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

        val companionSpec = TypeSpec.companionObjectBuilder()
            .addFunction(
                FunSpec.builder("create")
                    .addParameter("application", APPLICATION)
                    .returns(APPLICATION_COMPONENT)
                    .addStatement("return %M<%T>().create(application)", CREATE_GRAPH_FACTORY, factory)
                    .build()
            )
            .build()

        val graphSpec = TypeSpec.interfaceBuilder(graph)
            .addSuperinterface(APPLICATION_COMPONENT)
            .addAnnotation(
                AnnotationSpec.builder(DEPENDENCY_GRAPH)
                    .addMember("scope = %T::class", APPLICATION_SCOPE)
                    .build()
            )
            .addType(factorySpec)
            .addType(companionSpec)
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

    private companion object {
        const val META_PACKAGE = "com.deliveryhero.whetstone.meta"
        const val AUTO_INJECTOR_BINDING = "$META_PACKAGE.AutoInjectorBinding"
        const val AUTO_INSTANCE_BINDING = "$META_PACKAGE.AutoInstanceBinding"
        const val CONTRIBUTES_INJECTOR = "com.deliveryhero.whetstone.injector.ContributesInjector"
        const val CONTRIBUTES_APP_INJECTOR = "com.deliveryhero.whetstone.app.ContributesAppInjector"

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
        val CREATE_GRAPH_FACTORY = MemberName(METRO, "createGraphFactory")

        val APPLICATION = ClassName("android.app", "Application")
        val APPLICATION_COMPONENT = ClassName("com.deliveryhero.whetstone.app", "ApplicationComponent")
        val APPLICATION_SCOPE = ClassName("com.deliveryhero.whetstone.app", "ApplicationScope")
    }
}
