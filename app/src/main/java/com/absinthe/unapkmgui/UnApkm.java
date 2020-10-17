package com.absinthe.unapkmgui;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.PwHash;
import com.goterl.lazycode.lazysodium.interfaces.SecretStream;
import com.sun.jna.NativeLong;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class UnApkm {
    private static final String HEXES = "0123456789ABCDEF";
    public static final long MEM_LIMIT = 0x20000000;

    private UnApkm() {
    }

    private static byte[] getBytes(InputStream i, int num) throws IOException {
        byte[] data = new byte[num];
        i.read(data, 0, data.length);
        return data;
    }

    private static int byteToInt(byte[] b) {
        int i = 0, result = 0, shift = 0;

        while (i < b.length) {
            byte be = b[i];
            result |= (be & 0xff) << shift;
            shift += 8;
            i += 1;
        }

        return result;
    }

    public static String getHex(byte[] raw) {
        int max = Math.min(100, raw.length);
        final StringBuilder hex = new StringBuilder(2 * max);
        for (int i = 0; i < max; i++) {
            byte b = raw[i]; //raw[raw.length-i-1];
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

    static class Header {
        byte[] pwHashBytes, outputHash;
        long chunkSize;

        Header(byte[] pwHashBytes, byte[] outputHash, long chunkSize) {
            this.pwHashBytes = pwHashBytes;
            this.outputHash = outputHash;
            this.chunkSize = chunkSize;
        }
    }

    public static Header processHeader(InputStream i, LazySodiumAndroid lazySodium) throws IOException {
        return processHeader(i, lazySodium, true);
    }

    public static Header processHeader(InputStream i, LazySodiumAndroid lazySodium, boolean expensiveOps) throws IOException {
        return processHeader(i, lazySodium, expensiveOps, MEM_LIMIT);
    }

    public static Header processHeader(InputStream i, LazySodiumAndroid lazySodium, boolean expensiveOps, long upperMemLimit) throws IOException {
        getBytes(i, 1); // skip

        byte alg = getBytes(i, 1)[0];
        if (alg > 2 || alg < 1) {
            throw new IOException("incorrect algo");
        }

        PwHash.Alg algo = PwHash.Alg.valueOf(alg);

        long opsLimit = byteToInt(getBytes(i, 8));
        int memLimit = byteToInt(getBytes(i, 8));

        if (memLimit < 0 || memLimit > upperMemLimit) {
            throw new IOException("too much memory aaah");
        }

        byte[] en = getBytes(i, 8);
        long chunkSize = byteToInt(en);

        byte[] salt = getBytes(i, 16);
        byte[] pwHashBytes = getBytes(i, 24);


        byte[] outputHash = new byte[32];
        if(expensiveOps)
            lazySodium.cryptoPwHash(outputHash, 32, "#$%@#dfas4d00fFSDF9GSD56$^53$%7WRGF3dzzqasD!@".getBytes(), 0x2d, salt, opsLimit, new NativeLong(memLimit), algo);

        return new Header(pwHashBytes, outputHash, chunkSize);
    }

    public static InputStream decryptStream(InputStream i) throws IOException {
        LazySodiumAndroid lazySodium = new LazySodiumAndroid(new SodiumAndroid());
        Header h  = processHeader(i, lazySodium);
        return decryptStream(i, h, lazySodium);
    }

    public static InputStream decryptStream(InputStream i, Header h) throws IOException {
        LazySodiumAndroid lazySodium = new LazySodiumAndroid(new SodiumAndroid());
        processHeader(i, lazySodium, false);
        return decryptStream(i, h, lazySodium);
    }

    public static InputStream decryptStream(final InputStream i, final Header h, final LazySodiumAndroid lazySodium) throws IOException {
        final PipedInputStream pipedInputStream = new PipedInputStream();
        final PipedOutputStream pipedOutputStream = new PipedOutputStream();

        pipedInputStream.connect(pipedOutputStream);

        Thread pipeWriter = new Thread() {
            public void run () {
                try {
                    SecretStream.State state = new SecretStream.State();
                    lazySodium.cryptoSecretStreamInitPull(state, h.pwHashBytes, h.outputHash);

                    long chunkSizePlusPadding = h.chunkSize + 0x11;
                    byte[] cipherChunk = new byte[(int) chunkSizePlusPadding];

                    int bytesRead = 0;

                    while ( (bytesRead = i.read(cipherChunk)) != -1) {
                        int tagSize = 1;

                        byte[] decryptedChunk = new byte[ (int) h.chunkSize ];
                        byte[] tag = new byte[tagSize];

                        boolean success = lazySodium.cryptoSecretStreamPull(state, decryptedChunk, tag, cipherChunk, bytesRead);

                        if (!success) {
                            throw new IOException("decrypto error");
                        }
                        pipedOutputStream.write(decryptedChunk);
                        Arrays.fill(cipherChunk, (byte) 0);
                    }
                } catch (IOException e) {
                    if(!e.getMessage().equals("Pipe closed")) {
                        e.printStackTrace();
                    }
                } finally {
                    try {
                        pipedOutputStream.close();
                    } catch(IOException ignored) {}
                }
            }
        };

        pipeWriter.start();
        return pipedInputStream;
    }

    public static void decryptFile(InputStream is, OutputStream os) {
        try {
            InputStream toOut = decryptStream(is);

            // fix zip format if missing end signature
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));
            ZipInputStream zipIn = new ZipInputStream(toOut);

            ZipEntry entry = zipIn.getNextEntry();

            while (entry != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));

                byte[] bytesIn = new byte[4096];
                int read;
                while ((read = zipIn.read(bytesIn)) != -1) {
                    zos.write(bytesIn, 0, read);
                }
                zos.closeEntry();
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
            zipIn.close();
            zos.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}
