package com.seanproctor.onvifcamera

public object OnvifCommands {
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

    internal const val profilesCommand = (
            soapHeader
                    + "<GetProfiles xmlns=\"http://www.onvif.org/ver10/media/wsdl\"/>"
                    + envelopeEnd
            )

    public fun getStreamURICommand(profile: MediaProfile, protocol: String = "RTSP"): String {
        return (soapHeader
                + "<GetStreamUri xmlns=\"http://www.onvif.org/ver20/media/wsdl\">"
                + "<ProfileToken>${profile.token}</ProfileToken>"
                + "<Protocol>${protocol}</Protocol>"
                + "</GetStreamUri>"
                + envelopeEnd
                )
    }

    public fun getSnapshotURICommand(profile: MediaProfile): String {
        return (soapHeader + "<GetSnapshotUri xmlns=\"http://www.onvif.org/ver20/media/wsdl\">"
                + "<ProfileToken>${profile.token}</ProfileToken>"
                + "</GetSnapshotUri>" + envelopeEnd)
    }

    internal fun probeCommand(messageId: String): String {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" " +
                "xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">" +
                "<s:Header>" +
                "<a:Action s:mustUnderstand=\"1\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>" +
                "<a:MessageID>uuid:$messageId</a:MessageID>" +
                "<a:ReplyTo>" +
                "<a:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</a:Address>" +
                "</a:ReplyTo>" +
                "<a:To s:mustUnderstand=\"1\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>" +
                "</s:Header>" +
                "<s:Body>" +
                "<Probe xmlns=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">" +
                "<d:Types " +
                "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" " +
                "xmlns:dp0=\"http://www.onvif.org/ver10/network/wsdl\">" +
                "dp0:NetworkVideoTransmitter" +
                "</d:Types>" +
                "</Probe>" +
                "</s:Body>" +
                "</s:Envelope>"
    }

    internal const val deviceInformationCommand = (
            soapHeader
                    + "<GetDeviceInformation xmlns=\"http://www.onvif.org/ver10/device/wsdl\">"
                    + "</GetDeviceInformation>"
                    + envelopeEnd
            )

    internal const val servicesCommand = (
            soapHeader
                    + "<GetServices xmlns=\"http://www.onvif.org/ver10/device/wsdl\">"
                    + "<IncludeCapability>false</IncludeCapability>"
                    + "</GetServices>"
                    + envelopeEnd
            )

    internal const val getSystemDateAndTimeCommand = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\"" +
            " xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\">" +
            " <SOAP-ENV:Body>" +
            "  <tds:GetSystemDateAndTime/>" +
            " </SOAP-ENV:Body>" +
            "</SOAP-ENV:Envelope> "
}