// SPDX-License-Identifier: GPL-3.0-only
package one.hbx.exitcontroller;
import android.content.*;
import android.net.*;
import android.security.keystore.*;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.util.*;

final class GatewayClient {
    private final Context context;
    JSONObject pairing;
    private SSLSocketFactory tls;
    static SharedPreferences preferences(Context c) { return c.getSharedPreferences("MainActivity",Context.MODE_PRIVATE); }
    GatewayClient(Context context) throws Exception { this.context=context.getApplicationContext(); try { loadPairing(); } catch(Exception invalid) { pairing=null; } }
    GatewayClient(Context context,JSONObject config) throws Exception {
        this.context=context.getApplicationContext();validatePairing(config);pairing=config;configureTls();savePairing(config);
    }
    void validatePairing(JSONObject p) throws Exception {
        PairingEndpoint.validate(p.getString("endpoint"));
        if(!p.getString("token").matches("[A-Za-z0-9_-]{43}")) throw new IOException();
        java.security.cert.X509Certificate cert=(java.security.cert.X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(p.getString("certificate").getBytes(StandardCharsets.US_ASCII)));
        cert.checkValidity();
    }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(!store.containsAlias("gateway-pairing")) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("gateway-pairing",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); gen.generateKey();
        }
        return (SecretKey)store.getKey("gateway-pairing",null);
    }
    void savePairing(JSONObject p) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        preferences(context).edit().putString("iv",Base64.getEncoder().encodeToString(cipher.getIV()))
            .putString("pairing",Base64.getEncoder().encodeToString(cipher.doFinal(p.toString().getBytes(StandardCharsets.UTF_8)))).apply();
    }
    void loadPairing() throws Exception {
        String encrypted=preferences(context).getString("pairing",null); if(encrypted==null) return;
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.getDecoder().decode(preferences(context).getString("iv",""))));
        pairing=new JSONObject(new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)),StandardCharsets.UTF_8));
        validatePairing(pairing); configureTls();
    }
    void configureTls() throws Exception {
        KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType()); trust.load(null);
        trust.setCertificateEntry("gateway",CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(pairing.getString("certificate").getBytes(StandardCharsets.US_ASCII))));
        TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); factory.init(trust);
        SSLContext context=SSLContext.getInstance("TLS"); context.init(null,factory.getTrustManagers(),null); tls=context.getSocketFactory();
    }
    boolean vpnConnected() {
        ConnectivityManager c=context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps=c.getNetworkCapabilities(c.getActiveNetwork());
        return caps!=null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }
    JSONObject request(String path,JSONObject body) throws Exception {
        if(!vpnConnected()) throw new IOException("Connect Tailscale to reach your exit node.");
        HttpsURLConnection c=(HttpsURLConnection)new URL(pairing.getString("endpoint")+path).openConnection(java.net.Proxy.NO_PROXY);
        c.setSSLSocketFactory(tls); c.setConnectTimeout(5000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Authorization","Bearer "+pairing.getString("token"));
        try {
            if(body!=null) {
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json");
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()) { out.write(bytes); }
            }
            int code=c.getResponseCode();
            if(code==401) throw new IOException("Pairing was revoked. Import a new pairing file.");
            if(code==403) throw new IOException("This Tailscale device is not allowed to control the gateway.");
            if(code<200 || code>=300) throw new IOException(code==409?"Another change is running. Refresh to check its progress.":"Gateway request failed. Refresh before trying again.");
            try(InputStream in=c.getInputStream()) {
                byte[] bytes=in.readNBytes(1_000_001); if(bytes.length>1_000_000) throw new IOException();
                return new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            }
        } finally { c.disconnect(); }
    }
}
