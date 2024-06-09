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
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;

public record HttpRequestHeader(
    String method,
    String requestURI,
    String queryString,
    Map<String, List<String>> headers) {

  private static final Logger log = LoggerFactory.getLogger(HttpRequestHeader.class);

  public static final String CRLF = "\r\n";

  private static void addHeader(Map<String, List<String>> headers, String name, String value) {
    List<String> values = headers.computeIfAbsent(name, k -> new ArrayList<>());
    values.add(value);
  }

  // get the header case insensitive
  private static List<String> getHeader(String name, Map<String, List<String>> headers) {
    return headers.entrySet()
        .stream()
        .filter(me -> StringUtils.equalsIgnoreCase(name, me.getKey()))
        .flatMap(me -> me.getValue().stream())
        .toList();
  }

  public static HttpRequestHeader fromRequest(HttpServletRequest req, String remoteHost, int remotePort) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    Enumeration<String> headerNames = req.getHeaderNames();
    if(headerNames != null) {
      while(headerNames.hasMoreElements()) {
        String name = headerNames.nextElement();
        Enumeration<String> values = req.getHeaders(name);
        if(values != null) {
          while(values.hasMoreElements()) {
            String v = values.nextElement();
            if(StringUtils.equalsIgnoreCase(name, "host")) {
              String hostAndPort = remotePort!=-1?remoteHost+":"+remotePort:remoteHost;
              addHeader(headers, name, hostAndPort);
            } else if(StringUtils.equalsIgnoreCase(name, "expect")) {
              // ignore the expect header, it seems like tomcat has already dealt with it (between client and this reverse proxy).
              // no need to send it to the downstream server as it just makes the protocol more complicated for no gain IMHO.
              log.debug("ignoring 'expect' header from client, not sending it to the downstream server, '{}: {}'", name, v);
            } else {
              addHeader(headers, name, v);
            }
          }
        }
      }
    }
    List<String> xForwardedHost = getHeader("X-Forwarded-Host", headers);
    List<String> xForwardedProto = getHeader("X-Forwarded-Proto", headers);
    if(xForwardedHost.isEmpty() && xForwardedProto.isEmpty()) {
      // only set them together and don't mess with them if a proxy before us already set them (partially)
      // not sure if those headers are supposed to be multi value? better just store the original host, protocol
      headers.put("X-Forwarded-Host", List.of(req.getHeader("host")));
      headers.put("X-Forwarded-Proto", List.of(req.getScheme()));
    }
    List<String> forwarded = getHeader("Forwarded", headers);
    List<String> newForwarded = new ArrayList<>(forwarded);
    newForwarded.add("by=%s;for=%s;host=%s;proto=%s".formatted(
        req.getLocalAddr(),
        req.getRemoteAddr(),
        req.getHeader("host"),
        req.getScheme()));
    headers.put("Forwarded", newForwarded);
    // X-Forwarded-For should be a single value header
    String xForwardedFor = req.getHeader("X-Forwarded-For");
    if(StringUtils.isBlank(xForwardedFor)) {
      headers.put("X-Forwarded-For", List.of("%s,%s".formatted(req.getRemoteAddr(), req.getLocalAddr())));
    } else {
      headers.put("X-Forwarded-For", List.of("%s,%s".formatted(xForwardedFor, req.getLocalAddr())));
    }
    List<String> via = getHeader("Via", headers);
    List<String> newVia = new ArrayList<>(via);
    newVia.add("HTTP/1.1 %s:%s".formatted(req.getLocalAddr(), req.getLocalPort()));
    headers.put("Via", newVia);
    return new HttpRequestHeader(req.getMethod(), req.getRequestURI(), req.getQueryString(), headers);
  }

  public byte[] toBytes() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try(PrintStream p = new PrintStream(out)) {
      p.printf("%s %s%s HTTP/1.1%s",
          method,
          requestURI,
          (StringUtils.isBlank(queryString)?"":"?"+queryString),
          CRLF);
      headers.forEach((k,v) -> {
        v.forEach(value -> {
          p.printf("%s: %s%s", k, value, CRLF);
        });
      });
      p.print(CRLF);
    }
    return out.toByteArray();
  }

  public boolean isTransferEncodingChunked() {
    if(headers == null) {
      return false;
    }
    return headers.entrySet()
        .stream()
        .filter(me -> StringUtils.equalsIgnoreCase(me.getKey(), "transfer-encoding"))
        .flatMap(me -> (me.getValue()!=null?me.getValue().stream():Stream.empty()))
        .filter(s -> StringUtils.equalsIgnoreCase("chunked", s))
        .findFirst()
        .map(s -> true)
        .orElse(false);
  }

}
