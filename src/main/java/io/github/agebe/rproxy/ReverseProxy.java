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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ReverseProxy {

  private static final Logger log = LoggerFactory.getLogger(ReverseProxy.class);

  private static final int BUF_SIZE = 64 * 1024;

  private static URL toUrl(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException e) {
      throw new HttpException(e);
    }
  }

  public static void forwardRequestStreamResult(
      String remoteBaseUrl,
      HttpServletRequest request,
      HttpServletResponse response,
      RequestHeaderModifier requestHeaderModifier,
      ResponseHeaderModifier responseHeaderModifier,
      OutputStream respOut) {
    // FIXME stream the request body
    byte[] requestBody = getRequestBody(request);
    URL remote = toUrl(remoteBaseUrl);
    try (Socket socket = getSocket(remote)) {
      byte[] requestHeader = RequestHeaderModifier.fromRequest(request, remote.getHost(), requestHeaderModifier).toBytes();
      if (log.isTraceEnabled()) {
        log.trace("request header\n{}", HexDump
            .hexdump(requestHeader)
            .stream()
            .collect(Collectors.joining("\n")));
      }
      try (OutputStream out = socket.getOutputStream()) {
        out.write(requestHeader);
        if (requestBody != null) {
          out.write(requestBody);
        }
        out.flush();
        try (InputStream in = new BufferedInputStream(socket.getInputStream(), BUF_SIZE)) {
          HeaderParser parser = new HeaderParser(in);
          HttpHeadersParseResult parseResult = parser.parse();
          HttpHeaders headers = parseResult.headers();
          log.debug("http headers '{}'", headers);
          if (log.isTraceEnabled()) {
            log.trace("header bytes\n{}", HexDump
                .hexdump(parseResult.bytes())
                .stream()
                .collect(Collectors.joining("\n")));
          }
          setResponseHeaders(response, responseHeaderModifier!=null?responseHeaderModifier.apply(headers):headers);
          Long contentLength = ObjectUtils.asLong(headers.getHeader("Content-Length"));
          if (!hasResponseBody(request, headers)) {
            return;
          } else if (contentLength != null) {
            log.debug("read content-length '{}' bytes from stream ...", contentLength);
            byte[] buf = new byte[BUF_SIZE];
            long cl = contentLength;
            for (;;) {
              int read = in.read(buf, 0, (int) Math.min(BUF_SIZE, cl));
              respOut.write(buf, 0, read);
              cl -= read;
              log.trace("written '{}' bytes, '{}' bytes to go", read, cl);
              if (cl <= 0) {
                log.trace("reached content-length of '{}' bytes, break", contentLength);
                break;
              }
            }
          } else if (isTransferEncodingChunked(headers)) {
            log.debug("transfer encoding chunked");
            // do not write the http chunked protocol, let tomcat figure this out
            for (;;) {
              int chunkSize = getChunkSize(in);
              byte[] chunk = HttpUtils.nextChunk(in, chunkSize);
              if (chunkSize == 0) {
                break;
              }
              if (chunk != null) {
                respOut.write(chunk);
                log.debug("written chunked response, length '{}'", chunk.length);
              }
            }
            log.debug("transfer encoding chunked, done");
          } else {
            byte[] buf = new byte[BUF_SIZE];
            for (;;) {
              int read = in.read(buf);
              if (read == -1) {
                respOut.flush();
                break;
              }
              respOut.write(buf, 0, read);
            }
          }
        }
      } finally {
        respOut.flush();
      }
    } catch(Exception e) {
      throw new HttpException(e);
    }
  }

  private static boolean hasResponseBody(HttpServletRequest req, HttpHeaders headers) {
    if (StringUtils.equalsIgnoreCase("head", req.getMethod())) {
      return false;
    }
    int sc = headers.statusCode();
    // https://stackoverflow.com/questions/8628725/comprehensive-list-of-http-status-codes-that-dont-include-a-response-body
    return (sc >= 200) && (sc != 204) && (sc != 304);
  }

  private static byte[] getRequestBody(HttpServletRequest req) {
    try {
      return req.getInputStream().readAllBytes();
    } catch (Exception e) {
      return null;
    }
  }

  private static void setResponseHeaders(HttpServletResponse resp, HttpHeaders headers) {
    log.debug("set response status '{}'", headers.statusCode());
    resp.setStatus(headers.statusCode());
    headers.headers().forEach((k, l) -> {
      l.forEach(v -> {
        // ignore transfer encoding chunked as we don't control the connection to the
        // user-agent,
        // tomcat handles this. Not sure about the other transfer encoding values though
        // (compress, deflate, gzip)
        if (StringUtils.equalsIgnoreCase("Transfer-Encoding", k)) {
          log.debug("ignore header '{}', value '{}'", k, v);
        } else {
          log.debug("set response header '{}', value '{}'", k, v);
          resp.addHeader(k, v);
        }
      });
    });
  }

  private static boolean isTransferEncodingChunked(HttpHeaders headers) {
    List<String> encodings = headers.getHeaders("transfer-encoding");
    if (encodings != null) {
      for (String s : encodings) {
        if (StringUtils.containsIgnoreCase(s, "chunked")) {
          return true;
        }
      }
    }
    return false;
  }

  private static int getChunkSize(InputStream in) {
    byte[] chunkSizeBytes = HttpUtils.nextChunkSize(in);
    if ((chunkSizeBytes == null) || chunkSizeBytes.length == 0) {
      throw new HttpException("failed to read next chunk size from stream");
    }
    String chunkSizeString = new String(chunkSizeBytes);
    try {
      int chunkSize = Integer.parseInt(chunkSizeString, 16);
      log.debug("next chunk size hex '{}', '{}' bytes", chunkSizeString, chunkSize);
      return chunkSize;
    } catch (Exception e) {
      throw new HttpException("failed to convert hex chunk size '{}' to decimal", chunkSizeString);
    }
  }

  private static Socket getSocket(URL remote) {
    try {
      String protocol = remote.getProtocol();
      String host = remote.getHost();
      int port = remote.getPort();
      if ("https".equalsIgnoreCase(protocol)) {
        SSLContext sc = SSLContext.getInstance("TLS");
        // TODO make it configurable if the remote can be trusted
        // X509AllTrustManager might be required when the remote is e.g. using self signed certificates
        sc.init(null, new TrustManager[] { new X509AllTrustManager() }, null);
        SSLSocketFactory ssf = sc.getSocketFactory();
        SSLSocket s = (SSLSocket) ssf.createSocket(host, (port == -1 ? 443 : port));
        s.setSoTimeout(30000);
        s.setKeepAlive(false);
        s.startHandshake();
        return s;
      } else if ("http".equalsIgnoreCase(protocol)) {
        Socket s = new Socket(host, (port == -1 ? 80 : port));
        return s;
      } else {
        throw new HttpException("failed to open socket, protocol in '{}' not supported", remote);
      }
    } catch (Exception e) {
      throw new HttpException("failed to open socket to '{}'", remote, e);
    }
  }

}
