package com.lewisenator.phoenixnotes;

final class AppVersion {

    private AppVersion() {}

    /** The version from the jar's manifest, or "dev" when running from source. */
    static String current() {
        var version = AppVersion.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
