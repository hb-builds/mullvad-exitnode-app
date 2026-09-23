package one.hbx.exitcontroller;

public final class PairingEndpointTest {
    public static void main(String[] args) {
        String[] valid={"https://100.64.1.20:8443","https://100.127.255.255","https://100.65.12.8:443"};
        for(String value:valid)if(!value.equals(PairingEndpoint.validate(value)))throw new AssertionError(value);
        String[] invalid={"http://100.64.1.20:8443","https://192.168.1.1:8443","https://example.com:8443",
            "https://100.63.255.255:8443","https://100.128.0.1:8443","https://100.64.256.1:8443",
            "https://user@100.64.1.20:8443","https://100.64.1.20:8443/path","https://100.64.1.20:8443/",
            "https://100.64.1.20:8443?token=x","https://100.64.1.20:8443#fragment",
            "https://100.064.1.20:8443","https://100.64.1.20:0","https://100.64.1.20:65536",
            "https://100.64.1.20:-1","https://[::1]:8443","https://100.64.1.20:8443\\@example.com"};
        for(String value:invalid){
            try{PairingEndpoint.validate(value);throw new AssertionError("Accepted unsafe endpoint: "+value);}
            catch(IllegalArgumentException expected){}
        }
        System.out.println("Pairing endpoint tests passed: "+(valid.length+invalid.length));
    }
}
