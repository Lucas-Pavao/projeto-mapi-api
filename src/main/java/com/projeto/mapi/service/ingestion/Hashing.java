package com.projeto.mapi.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

/** SHA-256 e canonicalização de JSON (chaves ordenadas) para o content_hash do bruto. */
public final class Hashing {

    private Hashing() {}

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** SHA-256 do JSON canonicalizado: mesmas chaves e valores => mesmo hash, qualquer ordem de chaves. */
    public static String contentHash(JsonNode payload) {
        StringBuilder sb = new StringBuilder();
        canonical(payload, sb);
        return sha256(sb.toString());
    }

    private static void canonical(JsonNode n, StringBuilder sb) {
        if (n == null || n.isNull()) {
            sb.append("null");
        } else if (n.isObject()) {
            List<String> names = new ArrayList<>();
            Iterator<String> it = n.fieldNames();
            while (it.hasNext()) names.add(it.next());
            Collections.sort(names);
            sb.append('{');
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(names.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\":");
                canonical(n.get(names.get(i)), sb);
            }
            sb.append('}');
        } else if (n.isArray()) {
            sb.append('[');
            for (int i = 0; i < n.size(); i++) {
                if (i > 0) sb.append(',');
                canonical(n.get(i), sb);
            }
            sb.append(']');
        } else {
            sb.append(n.toString());
        }
    }
}
