package com.pangod512.modhub.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.zip.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Bounded image decoding; no archive extraction and no native texture allocation. */
public final class LogoData {
  private LogoData() {}

  public static byte[] read(Path archive, String entryName) throws IOException {
    if (archive == null || entryName == null || entryName.isEmpty())
      throw new IOException("Sin icono");
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      ZipEntry entry = zip.getEntry(entryName);
      if (entry == null || entry.isDirectory() || entry.getSize() > 4194304)
        throw new IOException("Icono no válido");
      try (InputStream in = zip.getInputStream(entry)) {
        return read(in);
      }
    }
  }

  /** The caller owns the stream, including streams backed by Forge's virtual resource roots. */
  public static byte[] read(InputStream stream) throws IOException {
      byte[] encoded = Io.readLimited(stream, 4194304);
      try (ImageInputStream input =
          ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw new IOException("Formato de icono desconocido");
        ImageReader reader = readers.next();
        try {
          reader.setInput(input);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width < 1 || height < 1 || width > 2048 || height > 2048)
            throw new IOException("Dimensiones de icono no válidas");
          BufferedImage image = reader.read(0);
          BufferedImage square = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
          java.awt.Graphics2D graphics = square.createGraphics();
          try {
            double ratio = 128.0 / Math.max(width, height);
            int w = Math.max(1, (int) (width * ratio)), h = Math.max(1, (int) (height * ratio));
            graphics.drawImage(image, (128 - w) / 2, (128 - h) / 2, w, h, null);
          } finally {
            graphics.dispose();
          }
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          if (!ImageIO.write(square, "png", out)) throw new IOException("No se pudo leer el icono");
          return out.toByteArray();
        } finally {
          reader.dispose();
        }
      }
  }
}
