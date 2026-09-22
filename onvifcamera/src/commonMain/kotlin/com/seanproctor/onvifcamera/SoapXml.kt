package com.seanproctor.onvifcamera

import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlConfig

/**
 * The xmlutil format used for every ONVIF request and response.
 *
 * Each setting is pinned rather than inherited, so a future xmlutil default cannot silently
 * change what goes on the wire or tighten what parses:
 *
 * - `compact()` means no indentation, no XML declaration, XML 1.0. It is load-bearing: the
 *   builder otherwise defaults to XML 1.1 with `IfRequired`, which prefixes every request with
 *   `<?xml version='1.1' ?>`, and cameras generally run XML 1.0 SOAP stacks.
 * - `defaultToGenericParser` selects xmlutil's own reader and writer over the platform ones
 *   (StAX on the JVM, Android's on Android), so every target emits and parses identically
 *   rather than inheriting each platform's strictness.
 * - The policy stays lenient because cameras vary wildly in what they return. The unknown-child
 *   handling only takes effect when decoding, so sharing it with the encoder costs nothing.
 *
 * This deliberately builds on a plain [XmlConfig.DefaultBuilder] rather than the `XML.v1`
 * preset, whose `recommended_1_0_0()` baseline indents, switches to XML 1.1 and turns on strict
 * attribute, boolean and float parsing.
 *
 * @param module resolves the polymorphic body type of the envelope being encoded or decoded.
 */
internal fun SoapXml(module: SerializersModule = EmptySerializersModule()): XML =
    XML(
        XmlConfig(
            XmlConfig.DefaultBuilder().apply {
                compact()
                defaultToGenericParser = true
                policy {
                    autoPolymorphic = true
                    pedantic = false
                    ignoreUnknownChildren()
                }
            }
        ),
        module,
    )
