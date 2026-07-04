package org.example.updater

fun main(args: Array<String>) {
    val installDir = UpdaterConfig.resolveInstallDir(args)
    val layout = InstallLayout(installDir)
    val log = UpdaterLog(layout.logFile)

    val endpoint = UpdaterConfig.resolveEndpoint(installDir, args)
    val fetcher: VersionFetcher = if (endpoint != null) {
        HttpVersionFetcher(endpoint, log)
    } else {
        log.warn("No version-check endpoint configured (updater.properties or --endpoint=); skipping update check")
        VersionFetcher { VersionCheckResult.Unreachable }
    }

    val updater = Updater(
        layout = layout,
        fetcher = fetcher,
        downloader = HttpDownloader(log),
        launcher = ProcessAppLauncher(log),
        replacer = Replacer(log),
        log = log,
    )

    updater.run()
}
