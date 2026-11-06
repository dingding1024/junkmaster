// AIDL interface for the Shizuku bindUserService user service.
// listCandidates is read-only dry-run; deletePaths deletes in-process via NIO
// with a second PathGuard check. Never clear full application data.
// NOTE: keep this file pure ASCII. The Android AIDL compiler on Windows may
// fail with MalformedInputException when reading non-ASCII comments.
package io.github.junkmaster.app.shizuku;

import io.github.junkmaster.app.shizuku.ShellResult;
import io.github.junkmaster.app.shizuku.TrimResult;

interface ICleanerUserService {

    /** Probe user service runtime (uid / sdk / alive), read-only. */
    ShellResult probe();

    /** Read-only dry-run candidate enumeration. filterJson is a simple hint. */
    List<String> listCandidates(String filterJson);

    /** In-process NIO deletion; each path is verified by PathGuard again. */
    ShellResult deletePaths(in List<String> paths);

    /** Probe trim-caches and record raw output; App side decides fallback. */
    TrimResult trimCaches(long desiredBytes);

    /** Best-effort hidden API cache clear; report failure honestly. */
    ShellResult clearCacheViaBinder(String packageName);

    /** Read cache bytes for a package; returns 0 when unavailable. */
    long queryCacheBytes(String packageName);

    /** unbindUserService does not kill the process, so destroy explicitly. */
    void destroy();
}
