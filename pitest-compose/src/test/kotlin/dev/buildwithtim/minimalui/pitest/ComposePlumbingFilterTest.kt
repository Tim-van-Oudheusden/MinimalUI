package dev.buildwithtim.minimalui.pitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pitest.classinfo.ClassName
import org.pitest.mutationtest.build.InterceptorType
import org.pitest.mutationtest.build.MutationInterceptorFactory
import org.pitest.mutationtest.engine.Location
import org.pitest.mutationtest.engine.MutationDetails
import org.pitest.mutationtest.engine.MutationIdentifier
import java.util.ServiceLoader

class ComposePlumbingFilterTest {
    private val composable = "(Ljava/lang/String;Landroidx/compose/runtime/Composer;I)V"
    private val plain = "(Landroid/content/pm/ApplicationInfo;)Z"

    private val math = "org.pitest.mutationtest.engine.gregor.mutators.MathMutator"
    private val voidCall = "org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator"
    private val negate = "org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator"

    private fun mutation(
        descriptor: String,
        mutator: String,
        description: String,
    ) = MutationDetails(
        MutationIdentifier(
            Location.location(ClassName.fromString("dev.example.ScreenKt"), "Screen", descriptor),
            7,
            mutator,
        ),
        "Screen.kt",
        description,
        12,
        0,
    )

    private fun kept(vararg mutations: MutationDetails): List<String> =
        ComposePlumbingFilter.intercept(mutations.toList(), null).map { it.description }

    @Test
    fun `drops bit and shift mutants of the Compose compiler's change masks inside composables`() {
        val kept =
            kept(
                mutation(composable, math, "Replaced bitwise AND with OR"),
                mutation(composable, math, "Replaced bitwise OR with AND"),
                mutation(composable, math, "Replaced XOR with AND"),
                mutation(composable, math, "Replaced Shift Left with Shift Right"),
                mutation(composable, math, "Replaced Shift Right with Shift Left"),
                mutation(composable, math, "Replaced float addition with subtraction"),
            )

        assertEquals(listOf("Replaced float addition with subtraction"), kept)
    }

    // InstalledApps reads ApplicationInfo.FLAG_SYSTEM with `and`; that is the app's own logic.
    @Test
    fun `keeps bit mutants outside composables`() {
        assertEquals(
            listOf("Replaced bitwise AND with OR"),
            kept(mutation(plain, math, "Replaced bitwise AND with OR")),
        )
    }

    @Test
    fun `drops removed calls to the Compose runtime's bookkeeping and keeps removed calls the app wrote`() {
        val kept =
            kept(
                mutation(
                    composable,
                    voidCall,
                    "removed call to androidx/compose/runtime/Composer::updateRememberedValue",
                ),
                mutation(
                    composable,
                    voidCall,
                    "removed call to androidx/compose/runtime/ComposerKt::sourceInformation",
                ),
                mutation(composable, voidCall, "removed call to androidx/compose/runtime/ComposerKt::traceEventStart"),
                mutation(
                    composable,
                    voidCall,
                    "removed call to androidx/compose/runtime/ScopeUpdateScope::updateScope",
                ),
                mutation(composable, voidCall, "removed call to androidx/compose/runtime/Updater::set-impl"),
                mutation(
                    composable,
                    voidCall,
                    "removed call to androidx/compose/runtime/ComposablesKt::invalidApplier",
                ),
                mutation(composable, voidCall, "removed call to androidx/compose/runtime/EffectsKt::LaunchedEffect"),
                mutation(composable, voidCall, "removed call to androidx/compose/runtime/MutableState::setValue"),
                mutation(composable, voidCall, "removed call to kotlin/jvm/functions/Function0::invoke"),
            )

        assertEquals(
            listOf(
                "removed call to androidx/compose/runtime/EffectsKt::LaunchedEffect",
                "removed call to androidx/compose/runtime/MutableState::setValue",
                "removed call to kotlin/jvm/functions/Function0::invoke",
            ),
            kept,
        )
    }

    @Test
    fun `keeps every other mutant inside composables`() {
        assertEquals(listOf("negated conditional"), kept(mutation(composable, negate, "negated conditional")))
    }

    // PIT finds the filter through java.util.ServiceLoader and runs it only when its feature is on by default.
    @Test
    fun `PIT loads the filter as a feature that is on by default`() {
        val factory =
            ServiceLoader
                .load(
                    MutationInterceptorFactory::class.java,
                ).single { it is ComposePlumbingFilterFactory }

        assertTrue(factory.provides().isOnByDefault)
        assertEquals(InterceptorType.FILTER, factory.createInterceptor(null).type())
    }
}
