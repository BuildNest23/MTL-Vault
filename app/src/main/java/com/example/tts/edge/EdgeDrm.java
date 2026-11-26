package com.example.tts.edge;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates the {@code Sec-MS-GEC} token that Microsoft's Read Aloud (Edge TTS) service requires.
 *
 * This is a port of the token logic from pagina_reader's {@code rust-core/src/tts/drm.rs}, which in
 * turn follows the Python {@code edge-tts} package. It is plain Java (all 32-bit math wraps like
 * Rust's wrapping_* operations) so it can be unit-tested without Android.
 */
public final class EdgeDrm {
    private EdgeDrm() {}

    public static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";

    private static final double WIN_EPOCH = 11_644_473_600.0;
    private static final double S_TO_NS = 1e9;
    private static final long MASK = 0xFFFFFFFFL;

    private static final int[] SHA256_INITIAL = {
            0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19
    };
    private static final int[] SHA256_STATE = {
            0xFD9FBEA2, 0x91243CE7, 0xBE499094, 0xCD2E1E0A, 0x6C006F49, 0x04BD35AB, 0xFEB0A7E4, 0x03EFFBD2
    };
    private static final int[] SHA256_SCHEDULE = {
            0xEB24BF9E, 0x1558691F, 0xA0C09EE2, 0x0CFA32BA, 0xA622EE8C, 0xD1DD9491, 0xC0DD25A0, 0xF9E70AB6,
            0xF0C31573, 0x7058CE4E, 0x7188E606, 0x00E5AB33, 0xD258B060, 0x72766564
    };
    private static final int[] K = {
            0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
            0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
            0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
            0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
            0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
            0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
            0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
            0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2
    };
    private static final int ADJUSTMENT = 0xC9DC06A7;
    private static final int SIGMA0_ADJ = 0x059C0B80;
    private static final int SIGMA1_ADJ = 0x000B92C0;

    private static final int[][] CS64_COEFFICIENTS = {
            {0xE82E91B1, 0xB32700D0}, {0x44DE24C6, 0x88DFF51D}, {0x3BEB65BC, 0x29514038},
            {0xC89C5933, 0xB328CBDF}, {0x8D1EC211, 0xD405B4D5}, {0x314EF5D5, 0x26E55F3C},
            {0xE92491B1, 0x607801C6}, {0xF0A5B9D5, 0x82B2C1FB}, {0x06064CF3, 0xFA51A3DA},
            {0xDA79FA60, 0x207CC464}, {0xBED20560, 0x3C3C74F3}, {0x89F9C9F8, 0xD2D14B61},
            {0x35FA38C6, 0xBB04A2C1}, {0xCB49E3C6, 0xC5626167}
    };
    private static final int CS64_FACTOR = 0x82893933;
    private static final int CS64_ADJ0 = 0xF2F90B1A;
    private static final int CS64_ADJ1 = 0x0B1A0000;

    private static volatile double clockSkewSeconds = 0.0;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ---------------------------------------------------------------- public API

    /** Token for the current time (corrected by any server clock skew we learned about). */
    public static String generateSecMsGec() {
        return generateSecMsGec(currentUnixTimestamp());
    }

    /** Token for an explicit unix timestamp in seconds (used by the unit test). */
    public static String generateSecMsGec(double unixTimestamp) {
        double ticks = unixTimestamp + WIN_EPOCH;
        ticks -= ticks % 300.0;
        ticks *= S_TO_NS / 100.0;
        String toHash = String.format(Locale.US, "%.0f", ticks) + TRUSTED_CLIENT_TOKEN;
        byte[] data = toHash.getBytes(StandardCharsets.UTF_8);
        byte[] digest = sha256Digest(data);
        int[] cs = cs64Digest(data, digest);
        byte[] csBytes = new byte[8];
        writeLe(csBytes, 0, cs[0]);
        writeLe(csBytes, 4, cs[1]);
        return hexUpper(csBytes) + "-" + hexUpper(digest);
    }

    public static double currentUnixTimestamp() {
        return System.currentTimeMillis() / 1000.0 + clockSkewSeconds;
    }

    /**
     * Call after the server rejects the handshake (HTTP 403) with its {@code Date} header value.
     * Learns how far the phone clock is off so the next token is correct.
     */
    public static boolean adjustClockSkewFromServerDate(String rfc1123Date) {
        if (rfc1123Date == null) return false;
        try {
            java.util.Date parsed = new java.text.SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(rfc1123Date);
            if (parsed == null) return false;
            double server = parsed.getTime() / 1000.0;
            clockSkewSeconds += server - currentUnixTimestamp();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String generateMuid() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return hexUpper(bytes);
    }

    // ---------------------------------------------------------------- internals

    private static int sigma0(int v) {
        return Integer.rotateRight(v, 7) ^ Integer.rotateRight(v, 18) ^ (v >>> 3);
    }

    private static int sigma1(int v) {
        return Integer.rotateRight(v, 17) ^ Integer.rotateRight(v, 19) ^ (v >>> 10);
    }

    private static int[] sha256Rounds(int[] state, int[] w, int firstRound) {
        int a = state[0], b = state[1], c = state[2], d = state[3];
        int e = state[4], f = state[5], g = state[6], h = state[7];
        for (int i = firstRound; i < 64; i++) {
            int constant = (firstRound == 14 && i == 14) ? ADJUSTMENT : K[i];
            int s1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
            int ch = g ^ (e & (f ^ g));
            int t1 = h + s1 + ch + constant + w[i];
            int s0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
            int maj = (a & b) | (c & (a | b));
            int t2 = s0 + maj;
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
        }
        return new int[]{a, b, c, d, e, f, g, h};
    }

    private static void expandSchedule(int[] w, int first) {
        for (int i = first; i < 64; i++) {
            w[i] = sigma1(w[i - 2]) + w[i - 7] + sigma0(w[i - 15]) + w[i - 16];
        }
    }

    private static int[] sha256Init(byte[] data, int off) {
        int[] w = new int[64];
        w[14] = ((data[off] & 0xFF) << 16) | ((data[off + 1] & 0xFF) << 8) | (data[off + 2] & 0xFF);
        w[15] = ((data[off + 3] & 0xFF) << 24) | ((data[off + 4] & 0xFF) << 16)
                | ((data[off + 5] & 0xFF) << 8) | (data[off + 6] & 0xFF);

        for (int index = 16; index < 30; index++) {
            int value = SHA256_SCHEDULE[index - 16];
            if (index - 2 >= 14) {
                int s1 = (index == 16) ? (sigma1(w[index - 2]) ^ SIGMA1_ADJ) : sigma1(w[index - 2]);
                value += s1;
            }
            if (index - 7 >= 14) {
                value += w[index - 7];
            }
            if (index - 15 >= 14) {
                int s0 = (index == 29) ? (sigma0(w[index - 15]) ^ SIGMA0_ADJ) : sigma0(w[index - 15]);
                value += s0;
            }
            w[index] = value;
        }

        w[30] = sigma1(w[28]) + w[23] + sigma0(w[15]) + w[14] + ADJUSTMENT - K[14];
        expandSchedule(w, 31);
        int[] working = sha256Rounds(SHA256_STATE, w, 14);

        int[] out = new int[8];
        for (int i = 0; i < 8; i++) {
            out[i] = SHA256_INITIAL[i] + working[i];
        }
        return out;
    }

    static byte[] sha256Digest(byte[] data) {
        if (data.length < 7) throw new IllegalArgumentException("Token salt must contain at least seven bytes");
        int length = 57 + data.length;
        int padZeros = Math.floorMod(55 - length, 64);

        int tail = data.length - 7;
        byte[] padded = new byte[tail + 1 + padZeros + 8];
        System.arraycopy(data, 7, padded, 0, tail);
        padded[tail] = (byte) 0x80;
        long bitLen = (long) length * 8L;
        for (int i = 0; i < 8; i++) {
            padded[padded.length - 1 - i] = (byte) (bitLen >>> (8 * i));
        }

        int[] state = sha256Init(data, 0);
        for (int offset = 0; offset + 64 <= padded.length; offset += 64) {
            int[] w = new int[64];
            for (int i = 0; i < 16; i++) {
                int p = offset + i * 4;
                w[i] = ((padded[p] & 0xFF) << 24) | ((padded[p + 1] & 0xFF) << 16)
                        | ((padded[p + 2] & 0xFF) << 8) | (padded[p + 3] & 0xFF);
            }
            expandSchedule(w, 16);
            int[] working = sha256Rounds(state, w, 0);
            for (int i = 0; i < 8; i++) {
                state[i] += working[i];
            }
        }

        byte[] digest = new byte[32];
        for (int i = 0; i < 8; i++) {
            digest[i * 4] = (byte) (state[i] >>> 24);
            digest[i * 4 + 1] = (byte) (state[i] >>> 16);
            digest[i * 4 + 2] = (byte) (state[i] >>> 8);
            digest[i * 4 + 3] = (byte) state[i];
        }
        return digest;
    }

    private static int readLe(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static void writeLe(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
    }

    private static int[] wordsLe4Even(byte[] data) {
        if (data.length == 0) return new int[0];
        int paddedLen = (data.length + 3) / 4 * 4;
        byte[] padded = new byte[paddedLen];
        System.arraycopy(data, 0, padded, 0, data.length);
        int count = paddedLen / 4;
        if (count % 2 == 1) count++;
        int[] words = new int[count];
        for (int i = 0; i < paddedLen / 4; i++) {
            words[i] = readLe(padded, i * 4);
        }
        return words;
    }

    private static long u(int v) {
        return v & MASK;
    }

    private static int[] cs64Digest(byte[] data, byte[] digest) {
        int keyA = readLe(digest, 0) | 1;
        int keyB = readLe(digest, 4) | 1;
        int[] factors = {keyA * CS64_FACTOR, keyB * CS64_FACTOR};

        List<Integer> terms = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        for (int idx = 0; idx < CS64_COEFFICIENTS.length; idx++) {
            terms.add(CS64_COEFFICIENTS[idx][0] * factors[idx % 2]);
            offsets.add(CS64_COEFFICIENTS[idx][1]);
        }

        byte[] prefixed = new byte[data.length + 1];
        System.arraycopy(data, 0, prefixed, 1, data.length);
        int[] words = wordsLe4Even(prefixed);
        for (int index = 0; index < words.length; index++) {
            int word = words[index];
            long k = u(index % 2 == 0 ? keyA : keyB);
            long term = u(word) * k;
            long mult = index % 2 == 0 ? 0xEF0569FBL : 0xC31713DBL;
            long offset = u(Integer.rotateLeft(word, 16)) * mult;
            if (index == 0) {
                term += u(CS64_ADJ0) * u(factors[0]);
                offset += u(CS64_ADJ1);
            }
            terms.add((int) term);
            offsets.add((int) offset);
        }

        int swapAcc = 0, swapLast = 0, state = 0, acc = 0;
        for (int index = 0; index < terms.size(); index += 2) {
            int x = terms.get(index);
            int y = terms.get(index + 1);

            int t = Integer.rotateLeft(offsets.get(index) + x, 16);
            int q = t * 0x76F50F34;
            swapAcc += Integer.rotateLeft(q, 16) * 0xC3EFEA97 + q;

            int t2 = Integer.rotateLeft(offsets.get(index + 1) + y, 16);
            int q2 = t2 * 0x1881CE3C;
            swapLast = Integer.rotateLeft(q2, 16) * 0x35BD1EC9 + q2;
            swapAcc += swapLast;

            state = state * keyA + x;
            state = Integer.rotateLeft(state, 16) * 0xCF98B111;
            state = Integer.rotateLeft(state, 16) * 0x87085B9F;
            state = Integer.rotateLeft(state, 16) * 0x12CEB96D;
            state = Integer.rotateLeft(state, 16) * 0x257E1D83;
            acc += state;

            int t3 = state * keyB + y;
            t3 = Integer.rotateLeft(t3, 16) * 0xA27416F5;
            t3 = Integer.rotateLeft(t3, 16) * 0xD38396FF;
            state = Integer.rotateLeft(Integer.rotateLeft(t3, 16) * 0x7C932B89, 16) * 0xBFA49F69;
            acc += state;
        }

        return new int[]{swapLast ^ state, swapAcc ^ acc};
    }

    private static String hexUpper(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
