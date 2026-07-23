package org.example.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.example.app.navigation.DefaultRootComponent
import org.example.app.ui.RootContent
import org.example.app.ui.TestTags
import org.example.app.ui.theme.ShareTheme
import org.example.shared.model.AppVersion
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = AppContainer()

    // §5.2: acquire the exclusive single-instance lock before any other startup work — a second
    // instance must never share the mic, the upload queue, or session directories.
    if (!container.singleInstanceLock.acquire()) {
        showAlreadyRunningAndExit(container)
        return
    }

    // §6.1: the cache must be loaded before the first Compose frame so RootComponent's initial
    // route (blocking vs. main menu) is correct from the very first render.
    container.configurationRepository.loadCached()

    // §8.4: crash recovery runs once at startup, before the menu shows.
    runCatching { container.recoverSessionsUseCase.recoverAll() }

    // §6.1 pt 3: a background refresh keeps the cache current without blocking first paint; uses
    // the injected dispatchers (§5.2), not an ad-hoc scope tied to nothing.
    val startupScope = CoroutineScope(SupervisorJob() + container.dispatchers.default)
    startupScope.launch(container.dispatchers.main) {
        container.refreshConfigurationUseCase.refresh()
    }

    // Decompose requires the component tree to be created (and all navigation to happen) on the
    // Swing EDT — the JVM `main` thread fails its main-thread check with NotOnMainThreadException.
    val lifecycle = LifecycleRegistry()
    val root = runOnUiThread {
        DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            container = container,
        ).also { lifecycle.resume() }
    }

    // §9/§11 updater failed-launch recovery: reaching this point means startup succeeded, so any
    // "update_pending.json" marker left by the updater (written right after a package swap) is
    // stale — delete it so the *next* updater run doesn't mistake this successful start for a
    // failed one and roll back. The updater launches the app from `<install_dir>` (§9 layout), so
    // the marker sits beside the process's working directory, not under AppDirectories/data/.
    runCatching { Files.deleteIfExists(Path.of(System.getProperty("user.dir")).resolve("update_pending.json")) }

    // Last-resort net: anything escaping the feature-level failure flows (recorder errors,
    // session-start rejection, …) lands in ErrorReporter instead of the JVM default handler's
    // error window + process death. Covers background threads and, transitively, coroutines —
    // uncaught coroutine exceptions fall through to the thread's default handler.
    Thread.setDefaultUncaughtExceptionHandler { _, e -> container.errorReporter.report(e) }

    application {
        val version = AppVersion(1, 0, 0)
        // Same net for the Compose UI thread: exceptions from composition/render/event handlers
        // are routed to the window exception handler, which by default kills the app.
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides WindowExceptionHandlerFactory {
                WindowExceptionHandler { e -> container.errorReporter.report(e) }
            },
        ) {
            Window(
                onCloseRequest = {
                    container.singleInstanceLock.release()
                    exitApplication()
                },
                title = "SHARE (v$version)",
            ) {
                ShareTheme {
                    RootContent(
                        component = root,
                        errorReporter = container.errorReporter,
                        onExitApp = {
                            container.singleInstanceLock.release()
                            exitProcess(1)
                        },
                    )
                }
            }
        }
    }
}

private fun <T> runOnUiThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var result: T? = null
    var error: Throwable? = null
    SwingUtilities.invokeAndWait {
        try {
            result = block()
        } catch (e: Throwable) {
            error = e
        }
    }
    error?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

/** §5.2: rendered as a Compose window (the app's only window in this path — nothing else is
 * running to show it "inside"), never a native/AWT dialog; bundled English fallback covers this
 * pre-config screen (§7) since no config can exist yet. */
private fun showAlreadyRunningAndExit(container: AppContainer) {
    val message = container.localizedStringProvider.resolveRaw("app.alreadyRunning", "en", null)
    val dismissLabel = container.localizedStringProvider.resolveRaw("error.dialog.dismiss", "en", null)
    application {
        Window(onCloseRequest = ::exitApplication, title = "SHARE") {
            MaterialTheme {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(message, modifier = Modifier.testTag(TestTags.Blocking.SINGLE_INSTANCE_MESSAGE))
                    Button(onClick = ::exitApplication) {
                        Text(dismissLabel)
                    }
                }
            }
        }
    }
    exitProcess(0)
}
