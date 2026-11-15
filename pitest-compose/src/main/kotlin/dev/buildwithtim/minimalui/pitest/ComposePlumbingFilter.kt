package dev.buildwithtim.minimalui.pitest

import org.pitest.bytecode.analysis.ClassTree
import org.pitest.mutationtest.build.InterceptorParameters
import org.pitest.mutationtest.build.InterceptorType
import org.pitest.mutationtest.build.MutationInterceptor
import org.pitest.mutationtest.build.MutationInterceptorFactory
import org.pitest.mutationtest.engine.Mutater
import org.pitest.mutationtest.engine.MutationDetails
import org.pitest.plugin.Feature

/**
 * Leaves out the mutants of what the Compose compiler adds to each composable (#412): the bit operations on its
 * `$changed`, `$dirty` and `$default` masks, and the calls into the runtime's own bookkeeping (groups, remembered
 * values, source information, tracing, recompose scopes, node updates). No test can kill most of them, each reruns
 * every test that renders the composable, and together they took about 60 % of the test runs of app's mutation run.
 *
 * Only composables are filtered, recognised by the [COMPOSER] parameter the compiler adds to each, so a bit operation
 * the app writes elsewhere is still mutated. Calls the app writes itself (`LaunchedEffect`, state writes) are kept.
 */
object ComposePlumbingFilter : MutationInterceptor {
    private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
    private const val MATH = "org.pitest.mutationtest.engine.gregor.mutators.MathMutator"
    private val bitDescription = Regex("""bitwise|XOR|Shift""")
    private val bookkeeping =
        listOf("Composer::", "ComposerKt::", "ScopeUpdateScope::", "Updater::", "ComposablesKt::invalidApplier")
            .map { "removed call to androidx/compose/runtime/$it" }

    override fun type(): InterceptorType = InterceptorType.FILTER

    override fun begin(clazz: ClassTree?) = Unit

    override fun intercept(
        mutations: Collection<MutationDetails>,
        mutater: Mutater?,
    ): Collection<MutationDetails> = mutations.filterNot(::isPlumbing)

    override fun end() = Unit

    private fun isPlumbing(mutation: MutationDetails): Boolean {
        if (COMPOSER !in mutation.id.location.methodDesc) return false
        val description = mutation.description
        return (mutation.mutator == MATH && bitDescription.containsMatchIn(description)) ||
            bookkeeping.any(description::startsWith)
    }
}

/** How PIT finds [ComposePlumbingFilter]: listed in META-INF/services, as the `FCOMPOSE` feature, on by default. */
class ComposePlumbingFilterFactory : MutationInterceptorFactory {
    override fun description(): String = "Filters mutants of the code the Compose compiler adds to composables"

    override fun provides(): Feature = Feature.named("FCOMPOSE").withOnByDefault(true).withDescription(description())

    override fun createInterceptor(params: InterceptorParameters?): MutationInterceptor = ComposePlumbingFilter
}
