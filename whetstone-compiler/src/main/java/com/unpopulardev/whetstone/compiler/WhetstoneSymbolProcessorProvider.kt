package com.unpopulardev.whetstone.compiler

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

public class WhetstoneSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        WhetstoneSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
}
