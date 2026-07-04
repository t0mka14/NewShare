package org.example.updater.fakes

import org.example.updater.AppLauncher
import org.example.updater.InstallLayout

class FakeAppLauncher : AppLauncher {
    var launchCount = 0
        private set

    override fun launch(layout: InstallLayout) {
        launchCount++
    }
}
