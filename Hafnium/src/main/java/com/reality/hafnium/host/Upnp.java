// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.host;

import com.reality.hafnium.Hafnium;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Asks the home router to forward a port to this computer, using UPnP, so
 * friends outside the house can reach the world being hosted. Many home
 * routers allow this. Some have it turned off, and some internet providers
 * share one address between many homes, which this can't get around.
 *
 * Only the router on this network is contacted. Nothing is sent anywhere else.
 */
public final class Upnp {

    /** What the router said: the address friends can use, or why it didn't work. */
    public record Result(String externalIp, String problem) {
        public boolean ok() { return problem == null; }
    }

    private static final String[] SERVICES = {
            "urn:schemas-upnp-org:service:WANIPConnection:2",
            "urn:schemas-upnp-org:service:WANIPConnection:1",
            "urn:schemas-upnp-org:service:WANPPPConnection:1",
    };
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private static String controlUrl;
    private static String serviceType;
    private static int mappedPort = -1;

    private Upnp() {}

    /** Forwards this TCP port. Takes a few seconds, so call it off the game's main thread. */
    public static Result open(int port) {
        try {
            if (!findRouter()) return new Result(null, "Your router didn't answer. UPnP may be turned off in its settings.");
            String localIp = localAddressTowards(URI.create(controlUrl).getHost());
            String body = "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + port + "</NewExternalPort>"
                    + "<NewProtocol>TCP</NewProtocol><NewInternalPort>" + port + "</NewInternalPort>"
                    + "<NewInternalClient>" + localIp + "</NewInternalClient><NewEnabled>1</NewEnabled>"
                    + "<NewPortMappingDescription>Hafnium</NewPortMappingDescription><NewLeaseDuration>0</NewLeaseDuration>";
            String reply = soap("AddPortMapping", body);
            if (reply == null) return new Result(null, "Your router refused to open a port for hosting.");
            mappedPort = port;
            String ext = tag(soap("GetExternalIPAddress", ""), "NewExternalIPAddress");
            if (ext == null || ext.isBlank()) return new Result(null, "Your router didn't say what your internet address is.");
            if (sharedAddress(ext)) {
                return new Result(null, "Your internet provider shares one address between many homes, so the router method can't work.");
            }
            return new Result(ext.trim(), null);
        } catch (Exception e) {
            Hafnium.LOGGER.info("UPnP didn't work: {}", e.toString());
            return new Result(null, "Your router didn't allow hosting.");
        }
    }

    /** Removes the forwarding again. */
    public static void close() {
        if (mappedPort < 0 || controlUrl == null) return;
        int port = mappedPort;
        mappedPort = -1;
        try {
            soap("DeletePortMapping", "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + port
                    + "</NewExternalPort><NewProtocol>TCP</NewProtocol>");
        } catch (Exception e) {
            Hafnium.LOGGER.info("Couldn't remove the router's port forwarding: {}", e.toString());
        }
    }

    // --- Finding the router ------------------------------------------------------------

    private static boolean findRouter() throws Exception {
        if (controlUrl != null) return true;
        String search = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n"
                + "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n";
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3000);
            byte[] out = search.getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(out, out.length, new InetSocketAddress("239.255.255.250", 1900)));
            byte[] buf = new byte[2048];
            long until = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < until) {
                DatagramPacket in = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(in);
                } catch (SocketTimeoutException e) {
                    return false;
                }
                String reply = new String(in.getData(), 0, in.getLength(), StandardCharsets.US_ASCII);
                String location = header(reply, "location");
                if (location != null && readDescription(location)) return true;
            }
        }
        return false;
    }

    private static boolean readDescription(String location) {
        try {
            String xml = HTTP.send(HttpRequest.newBuilder(URI.create(location)).timeout(Duration.ofSeconds(3)).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList services = doc.getElementsByTagName("service");
            for (String wanted : SERVICES) {
                for (int i = 0; i < services.getLength(); i++) {
                    Element s = (Element) services.item(i);
                    if (!wanted.equals(text(s, "serviceType"))) continue;
                    String control = text(s, "controlURL");
                    if (control == null) continue;
                    controlUrl = URI.create(location).resolve(control.trim()).toString();
                    serviceType = wanted;
                    return true;
                }
            }
        } catch (Exception e) {
            Hafnium.LOGGER.debug("Couldn't read the router's description at {}", location, e);
        }
        return false;
    }

    private static String soap(String action, String args) throws Exception {
        String envelope = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:" + action
                + " xmlns:u=\"" + serviceType + "\">" + args + "</u:" + action + "></s:Body></s:Envelope>";
        HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(controlUrl))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("SOAPAction", "\"" + serviceType + "#" + action + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope)).build(), HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200 ? res.body() : null;
    }

    // --- Small helpers -----------------------------------------------------------------

    private static String localAddressTowards(String routerHost) throws Exception {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName(routerHost), 1900);
            return s.getLocalAddress().getHostAddress();
        }
    }

    /** Addresses that only exist inside a home or an internet provider, never on the open internet. */
    private static boolean sharedAddress(String ip) {
        try {
            InetAddress a = InetAddress.getByName(ip.trim());
            byte[] b = a.getAddress();
            if (a.isSiteLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()) return true;
            return b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64; // 100.64.0.0/10
        } catch (Exception e) {
            return true;
        }
    }

    private static String header(String reply, String name) {
        for (String line : reply.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().toLowerCase(Locale.ROOT).equals(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static String text(Element parent, String tag) {
        NodeList n = parent.getElementsByTagName(tag);
        return n.getLength() == 0 ? null : n.item(0).getTextContent();
    }

    private static String tag(String xml, String tag) {
        if (xml == null) return null;
        int a = xml.indexOf("<" + tag + ">");
        int b = xml.indexOf("</" + tag + ">");
        return a < 0 || b < a ? null : xml.substring(a + tag.length() + 2, b);
    }
}
