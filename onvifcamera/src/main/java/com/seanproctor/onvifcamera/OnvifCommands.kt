package com.seanproctor.onvifcamera

internal object OnvifCommands {
    /**
     * The header for SOAP 1.2 with digest authentication
     */
    private const val soapHeader = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<soap:Envelope " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" >" +
            "<soap:Body>"

    private const val envelopeEnd = "</soap:Body></soap:Envelope>"

    internal const val profilesCommand: String = (
            soapHeader
                    + "<GetProfiles xmlns=\"http://www.onvif.org/ver10/media/wsdl\"/>"
                    + envelopeEnd
            )

    internal fun getStreamURICommand(profile: MediaProfile, protocol: String = "RTSP"): String {
        return (soapHeader
                + "<GetStreamUri xmlns=\"http://www.onvif.org/ver20/media/wsdl\">"
                + "<ProfileToken>${profile.token}</ProfileToken>"
                + "<Protocol>${protocol}</Protocol>"
                + "</GetStreamUri>"
                + envelopeEnd
                )
    }

    internal fun getSnapshotURICommand(profile: MediaProfile): String {

        return (soapHeader + "<GetSnapshotUri xmlns=\"http://www.onvif.org/ver20/media/wsdl\">"
                + "<ProfileToken>${profile.token}</ProfileToken>"
                + "</GetSnapshotUri>" + envelopeEnd)
    }

    internal const val deviceInformationCommand: String = (
            soapHeader
                    + "<GetDeviceInformation xmlns=\"http://www.onvif.org/ver10/device/wsdl\">"
                    + "</GetDeviceInformation>"
                    + envelopeEnd
            )

    internal const val servicesCommand: String = (
            soapHeader
                    + "<GetServices xmlns=\"http://www.onvif.org/ver10/device/wsdl\">"
                    + "<IncludeCapability>false</IncludeCapability>"
                    + "</GetServices>"
                    + envelopeEnd
            )
}
