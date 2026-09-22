package com.seanproctor.onvifcamera

/**
 * The two ONVIF media services. Media2 (Profile T era) is preferred: its `GetProfiles` returns
 * only what is asked for and its `Encoding` admits `H265`, which Media1's enumeration does not.
 * Media1 is the fallback for the many older cameras that offer nothing else. Profile tokens are
 * the same in both, so a device's profiles, stream URIs and snapshot URIs all come from
 * whichever one it offers.
 */
internal enum class MediaService(val namespace: String) {
    MEDIA2("http://www.onvif.org/ver20/media/wsdl"),
    MEDIA1("http://www.onvif.org/ver10/media/wsdl");

    companion object {
        /** Media2 if [namespaces] advertises it, else Media1, else null. */
        fun offeredBy(namespaces: Collection<String>): MediaService? =
            entries.firstOrNull { it.namespace in namespaces }
    }
}
