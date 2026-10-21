package com.pangod512.modhub.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class Io {
  private Io() {}

  static byte[] readLimited(InputStream input, int limit) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int count;
    while ((count = input.read(buffer, 0, Math.min(buffer.length, limit + 1 - output.size())))
        != -1) {
      output.write(buffer, 0, count);
      if (output.size() > limit) throw new IOException("Respuesta demasiado grande");
    }
    return output.toByteArray();
  }

  static String hex(byte[] bytes) {
    char[] alphabet = "0123456789abcdef".toCharArray(), result = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int b = bytes[i] & 255;
      result[i * 2] = alphabet[b >>> 4];
      result[i * 2 + 1] = alphabet[b & 15];
    }
    return new String(result);
  }
}
