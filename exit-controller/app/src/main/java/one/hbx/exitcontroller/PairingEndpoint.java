// SPDX-License-Identifier: GPL-3.0-only
package one.hbx.exitcontroller;

import java.net.URI;

/** Only literal Tailscale IPv4 addresses; no DNS, redirects or URL credentials. */
final class PairingEndpoint {
    static String validate(String value) {
        URI uri=URI.create(value);
        String host=uri.getHost();
        if(!"https".equals(uri.getScheme()) || host==null || uri.getRawUserInfo()!=null
            || uri.getRawQuery()!=null || uri.getRawFragment()!=null
            || (uri.getRawPath()!=null && !uri.getRawPath().isEmpty())
            || uri.getPort()==0 || uri.getPort()>65535
            || !host.matches("100\\.(?:[1-9][0-9]{0,2})\\.(?:0|[1-9][0-9]{0,2})\\.(?:0|[1-9][0-9]{0,2})"))
            throw new IllegalArgumentException("Expected an HTTPS Tailscale IPv4 endpoint");
        String[] octets=host.split("\\.");
        int second=Integer.parseInt(octets[1]);
        if(second<64 || second>127 || Integer.parseInt(octets[2])>255 || Integer.parseInt(octets[3])>255)
            throw new IllegalArgumentException("Address is outside the Tailscale range");
        if(!value.equals("https://"+host+(uri.getPort()==-1?"":":"+uri.getPort())))
            throw new IllegalArgumentException("Endpoint must be canonical");
        return value;
    }
}
