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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class HeaderParser {

  private static final int MAX_HEADER_SIZE = 64 * 1024;

  private InputStream in;

  private ByteArrayOutputStream out = new ByteArrayOutputStream();

  public HeaderParser(InputStream in) {
    super();
    this.in = in;
  }

  public HttpHeadersParseResult parse() {
    VersionStatus status = startHeader();
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for(;;) {
      String line = nextLine();
      if(line == null) {
        break;
      }
      String name = StringUtils.strip(StringUtils.substringBefore(line, ":"));
      String value = StringUtils.strip(StringUtils.substringAfter(line, ":"));
      List<String> values = headers.computeIfAbsent(name, k -> new ArrayList<>());
      values.add(value);
    }
    return new HttpHeadersParseResult(
        new HttpHeaders(
            status.version(),
            status.statusCode(),
            status.status(),
            Collections.unmodifiableMap(headers)),
        out.toByteArray());
  }

  private static Integer asInteger(Object o) {
    if(o == null) {
      return null;
    } else if(o instanceof Number) {
      return ((Number) o).intValue();
    } else if(o instanceof String) {
      try {
        return Integer.valueOf((String) o);
      } catch(Exception e) {
        return null;
      }
    } else {
      return null;
    }
  }

  private VersionStatus startHeader() {
    String line = nextLine();
    String[] parts = StringUtils.split(line);
    if(parts.length < 2) {
      throw new HttpException("expected http start header with 2 parts but got '{}'", line);
    }
    Integer code = asInteger(parts[1]);
    if(code == null) {
      throw new HttpException("failed to parse http status code from '{}'", line);
    }
    String status = (parts.length>=3)?parts[2]:null;
    return new VersionStatus(parts[0], code, status);
  }

  // returns null if there are no more http headers
  // TODO this currently does not work with multi-line header.
  // https://stackoverflow.com/a/31324422
  private String nextLine() {
    try {
      StringBuilder s = new StringBuilder();
      for(;;) {
        int i = nextByte();
        if(i == 0xd) {
          int i2 = nextByte();
          if(i2 == 0xa) {
            return s.isEmpty()?null:s.toString();
          } else {
            throw new HttpException("failed to parse http headers,"
                + " expected line feed after carriage return but got '{}'", Integer.toHexString(i2));
          }
        } else {
          // TODO check allowed http header character
          s.append((char)i);
          if(s.length() > MAX_HEADER_SIZE) {
            throw new HttpException("http header is to large (> '{}'", MAX_HEADER_SIZE);
          }
        }
      }
    } catch(IOException e) {
      throw new UncheckedIOException("failed in nextLine", e);
    }
  }

  private int nextByte() throws IOException {
    int i = in.read();
    if(i == -1) {
      throw new HttpException("unexpected end of stream while parsing http headers");
    }
    out.write(i);
    return i;
  }

}
