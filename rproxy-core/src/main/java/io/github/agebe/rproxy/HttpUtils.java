/*
 * Copyright 2024 Andre Gebers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.github.agebe.rproxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public class HttpUtils {

  public static byte[] nextChunk(InputStream in, int chunkSize) {
    try {
      byte[] buf = in.readNBytes(chunkSize);
      if(buf.length != chunkSize) {
        throw new ReverseProxyException("expected chunk with size '{}' but got '{}'", chunkSize, buf.length);
      }
      int cr = nextByte(in);
      // consume CR/LF so we can read the next chunk size
      if(cr != 0xd) {
        throw new ReverseProxyException("failed to read http chunk, expected CR after chunk but got '{}'", cr);
      }
      int lf = nextByte(in);
      if(lf != 0xa) {
        throw new ReverseProxyException("failed to read http chunk, expected LF after chunk but got '{}'", lf);
      }
      return buf;
    } catch(IOException e) {
      throw new UncheckedIOException("failed in nextLine", e);
    }
  }

  public static byte[] nextChunkSize(InputStream in) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      for(;;) {
        int i = nextByte(in);
        if(i == 0xd) {
          int i2 = nextByte(in);
          if(i2 == 0xa) {
            return out.size()==0?null:out.toByteArray();
          } else {
            throw new ReverseProxyException("failed to read chunk,"
                + " expected line feed after carriage return but got '{}'", Integer.toHexString(i2));
          }
        } else {
          out.write(i);
        }
      }
    } catch(IOException e) {
      throw new UncheckedIOException("failed in nextLine", e);
    }
  }

  private static int nextByte(InputStream in) throws IOException {
    int i = in.read();
    if(i == -1) {
      throw new ReverseProxyException("unexpected end of stream while parsing http headers");
    }
    return i;
  }

}
