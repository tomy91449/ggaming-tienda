package com.ggaming.bot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifica que un pedido al webhook venga de verdad de Meta.
 *
 * Meta calcula HMAC-SHA256(cuerpo del pedido, App Secret) y lo manda en la cabecera
 * "X-Hub-Signature-256: sha256=<hex>". Nosotros calculamos lo mismo y comparamos.
 * Sin el App Secret nadie puede fabricar una firma válida.
 */
public final class FirmaWebhook {

    private FirmaWebhook() {}

    public static boolean valida(String appSecret, byte[] cuerpo, String cabecera) {
        if (appSecret == null || appSecret.isBlank() || cabecera == null || !cabecera.startsWith("sha256=")) {
            return false;
        }
        byte[] esperada = calcular(appSecret, cuerpo);
        byte[] recibida;
        try {
            recibida = HexFormat.of().parseHex(cabecera.substring("sha256=".length()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        // Comparación en tiempo constante: no da pistas a quien intenta adivinar la firma.
        return MessageDigest.isEqual(esperada, recibida);
    }

    /** "sha256=<hex>" (lo usan los tests para simular a Meta). */
    public static String firmar(String appSecret, byte[] cuerpo) {
        return "sha256=" + HexFormat.of().formatHex(calcular(appSecret, cuerpo));
    }

    private static byte[] calcular(String appSecret, byte[] cuerpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(cuerpo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
