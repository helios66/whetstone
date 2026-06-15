plugins {
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.androidApp) apply false
    alias(libs.plugins.androidLib) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.binaryValidator)
    alias(libs.plugins.kotlinAndroid) apply false
}

apiValidation {
    ignoredProjects.addAll(listOf("sample", "sample-library", "whetstone-compiler"))
    // Metro emits contribution "hint" classes into the metro.hints package; they are an
    // implementation detail of the DI engine, not Whetstone's public API.
    ignoredPackages.add("metro.hints")
}

// Central-only: each published library module (whetstone, -compiler, -compose, -worker) targets the
// Sonatype Central Portal via vanniktech. Coordinates + the full POM (name/description/url/licence/
// developer/scm) + the sources jar + the javadoc jar are derived automatically from the GROUP /
// VERSION_NAME / POM_* gradle properties (root + per-module gradle.properties). Credentials and the
// GPG key are supplied at invocation as ORG_GRADLE_PROJECT_* env vars (see RELEASING.md).
subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral()   // Central Portal; USER_MANAGED (manual finalize in the Portal UI)
            signAllPublications()     // GPG-signs releases (snapshots are left unsigned by design)
        }
    }
}

// Refuse to upload to Central without a signing key present — Central rejects unsigned release
// bundles, and a silent unsigned attempt wastes an owner round-trip. publishToMavenLocal and
// snapshot publishes are unaffected. Mirrors the sibling library's publish guard.
allprojects {
    tasks.matching { it.name == "publishAllPublicationsToMavenCentralRepository" }.configureEach {
        doFirst {
            require(
                providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").orNull?.isNotBlank() == true ||
                    providers.gradleProperty("signingInMemoryKey").orNull?.isNotBlank() == true,
            ) {
                "Refusing to publish to Central without a signing key. Source the GPG key and pass it " +
                    "as ORG_GRADLE_PROJECT_signingInMemoryKey (see RELEASING.md)."
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.register("mergeLintReports") {
    description = "Merges all module lint XML reports into one file."

    val outputDir = layout.buildDirectory.dir("reports/lint")
    val mergedFile = outputDir.get().file("merged-lint-report.xml").asFile


    doLast {
        val lintReportsDir = File(rootDir, "build/reports/lint")

        val reports = lintReportsDir.walkTopDown()
            .filter { it.isFile && it.name == "lint-report.xml" }
            .toList()

        if (reports.isEmpty()) {
            logger.warn("⚠️ No lint reports found to merge.")
            return@doLast
        }

        val docBuilder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val mergedDoc = docBuilder.newDocument()
        val issuesElement = mergedDoc.createElement("issues")
        mergedDoc.appendChild(issuesElement)

        reports.forEach { report ->
            logger.info("✅ Merging: ${report.relativeTo(rootDir)}")
            val reportDoc = docBuilder.parse(report)
            val issueNodes = reportDoc.getElementsByTagName("issue")
            for (i in 0 until issueNodes.length) {
                val imported = mergedDoc.importNode(issueNodes.item(i), true)
                issuesElement.appendChild(imported)
            }
        }

        val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
        mergedFile.parentFile.mkdirs()
        transformer.transform(
            javax.xml.transform.dom.DOMSource(mergedDoc),
            javax.xml.transform.stream.StreamResult(mergedFile)
        )

        logger.info("✅ Merged lint report written to: ${mergedFile.absolutePath}")
    }
}

tasks.register("mergeDetektSarifReports") {
    description = "Merges all module Detekt SARIF reports into one file."

    val outputDir = layout.buildDirectory.dir("reports/detekt")
    val mergedFile = outputDir.get().file("merged-detekt-report.sarif").asFile

    doLast {
        val sarifReportsDir = File(rootDir, "build/reports/detekt")

        val reports = sarifReportsDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".sarif") }
            .toList()

        if (reports.isEmpty()) {
            logger.warn("⚠️ No Detekt SARIF reports found to merge.")
            return@doLast
        }

        val jsonSlurper = groovy.json.JsonSlurper()

        val mergedResults = mutableListOf<Map<String, Any>>()

        reports.forEach { report ->
            logger.info("✅ Merging: ${report.relativeTo(rootDir)}")
            val sarifData = jsonSlurper.parse(report) as Map<String, Any>
            val runs = sarifData["runs"] as List<Map<String, Any>>?

            runs?.forEach { run ->
                val results = run["results"] as List<Map<String, Any>>?
                results?.let {
                    mergedResults.addAll(it)
                }
            }
        }

        val mergedSarif = mapOf(
            "version" to "2.1.0",
            "runs" to listOf(
                mapOf(
                    "tool" to mapOf(
                        "driver" to mapOf(
                            "name" to "Detekt",
                            "version" to "1.18.0"
                        )
                    ),
                    "results" to mergedResults
                )
            )
        )

        val mergedJson = groovy.json.JsonOutput.toJson(mergedSarif)
        mergedFile.parentFile.mkdirs()
        mergedFile.writeText(mergedJson)

        logger.info("✅ Merged Detekt SARIF report written to: ${mergedFile.absolutePath}")
    }
}
