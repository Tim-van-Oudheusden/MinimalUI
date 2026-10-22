package dev.buildwithtim.minimalui

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import dev.buildwithtim.minimalui.core.LauncherSetup
import dev.buildwithtim.minimalui.core.SetupImport
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executor

/**
 * Export setup and Import setup (#208): the whole setup in [state] to and from a file the user picks in Android's file
 * picker. The picker hands over just that one file, so the launcher needs no storage permission and no network. The
 * file is read and written on [background], since its provider may be slow or online; the outcome shows on the main
 * thread. Made in the [activity]'s onCreate, which is when it may register for the picker's results.
 */
internal class SetupFiles(
    private val activity: ComponentActivity,
    private val state: LauncherState,
    private val background: Executor,
) {
    private val resolver get() = activity.contentResolver

    private val exporter =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE)) { file ->
            file?.let(::write)
        }

    private val importer =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { file -> file?.let(::read) }

    /** Asks for a new file, offering a name, then writes the setup as it is at that moment into it. */
    fun export() = exporter.launch(FILE_NAME)

    // Any type: a provider may not know a .json file's type, and a file that is not a setup is refused once read.
    fun import() = importer.launch(arrayOf("*/*"))

    /** The current setup replaced by [imported], once the user confirmed it. */
    fun replace(imported: LauncherSetup) {
        state.stores.replaceWith(imported)
        show(R.string.setup_imported)
    }

    private fun write(file: Uri) {
        val text = state.stores.setup.exported()
        background.execute {
            val written =
                attempt {
                    val output = resolver.openOutputStream(file) ?: throw FileNotFoundException(file.toString())
                    output.use { it.write(text.toByteArray()) }
                } != null
            activity.mainExecutor.execute {
                show(
                    if (written) R.string.setup_exported else R.string.setup_export_failed,
                )
            }
        }
    }

    private fun read(file: Uri) {
        background.execute {
            val text =
                attempt {
                    val input = resolver.openInputStream(file) ?: throw FileNotFoundException(file.toString())
                    input.use { it.readUpTo(MAX_BYTES) }
                }
            val imported = text?.let(LauncherSetup::imported)
            activity.mainExecutor.execute { offer(imported) }
        }
    }

    private fun offer(imported: SetupImport?) {
        when (imported) {
            null -> show(R.string.setup_read_failed)
            SetupImport.NotASetup -> show(R.string.setup_not_a_setup)
            SetupImport.TooNew -> show(R.string.setup_too_new)
            is SetupImport.Read -> state.push(Screen.ImportSetup(imported.setup))
        }
    }

    private fun show(
        @StringRes message: Int,
    ) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val MIME_TYPE = "application/json"
        const val FILE_NAME = "minimalui-setup.json"

        // A setup of thousands of apps stays far below this; a file past it is not one, and is never read whole.
        const val MAX_BYTES = 1 shl 20
        const val BUFFER_BYTES = 8192

        /** What [work] returns, or null when it fails as a file can: unreadable, gone, or no longer ours to use. */
        fun <T : Any> attempt(work: () -> T): T? =
            try {
                work()
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }

        /** The text this holds, decoded as UTF-8; none at all when it holds more than [limit] bytes. */
        fun InputStream.readUpTo(limit: Int): String {
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            var count = read(buffer)
            while (count >= 0 && bytes.size() <= limit) {
                bytes.write(buffer, 0, count)
                count = read(buffer)
            }
            return if (bytes.size() > limit) "" else bytes.toByteArray().decodeToString()
        }
    }
}
