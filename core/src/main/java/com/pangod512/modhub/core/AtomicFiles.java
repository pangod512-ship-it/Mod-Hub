package com.pangod512.modhub.core;

import java.io.IOException;
import java.nio.file.*;

/** Retry brief Windows sharing violations without truncating the existing file. */
final class AtomicFiles {
  private AtomicFiles() {}

  static void replace(Path temporary, Path destination) throws IOException {
    for (int attempt = 0; ; attempt++) {
      try {
        try {
          Files.move(
              temporary,
              destination,
              StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
          Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return;
      } catch (AccessDeniedException sharingViolation) {
        if (attempt >= 3) throw sharingViolation;
        try {
          Thread.sleep(20L << attempt);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IOException("Guardado interrumpido", interrupted);
        }
      }
    }
  }
}
