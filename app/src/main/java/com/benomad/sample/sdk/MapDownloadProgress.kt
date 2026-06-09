package com.benomad.sample.sdk

/**
 * Map-data progress, as a single sealed model the UI can render directly.
 *
 * Whether the SDK runs in **FULL** mode (the whole map is downloaded/deployed and the
 * app works offline) or **HYBRID** mode (only part of the map is on disk and the rest
 * is streamed on demand) is decided by the BeNomad license / LBO — there is no client
 * toggle. The app can only observe what happens:
 *
 * - FULL, first launch (or when an update is installed): [Downloading] then [Extracting]
 *   then [Ready].
 * - HYBRID: tiles stream in the background as the map is panned, reported as
 *   [StreamingHybrid]; the app is usable throughout.
 *
 * See [MapDataController].
 */
sealed interface MapDownloadProgress {

    /** No map activity in progress. */
    data object Idle : MapDownloadProgress

    /**
     * FULL mode: the base-map archive is downloading.
     * @param percent 0..100
     * @param downloadedKb kilobytes downloaded so far
     * @param totalKb total kilobytes to download
     */
    data class Downloading(val percent: Float, val downloadedKb: Float, val totalKb: Float) : MapDownloadProgress

    /** FULL mode: the downloaded archive is being extracted. [percent] is 0..100. */
    data class Extracting(val percent: Float) : MapDownloadProgress

    /** HYBRID mode: map tiles are streaming in the background. [percent] is 0..100. */
    data class StreamingHybrid(val percent: Float) : MapDownloadProgress

    /** Map data is ready to use. */
    data object Ready : MapDownloadProgress

    /** A download/streaming error occurred (e.g. no network, no storage). */
    data class Failed(val message: String) : MapDownloadProgress
}
