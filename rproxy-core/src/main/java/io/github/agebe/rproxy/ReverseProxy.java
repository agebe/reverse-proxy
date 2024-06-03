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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletInputStream;
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
    final String requestId = UUID.randomUUID().toString();
    URL remote = toUrl(remoteBaseUrl);
    try(Socket socket = getSocket(remote)) {
      log.info("forwarding '{} {}' to '{}'", request.getMethod(), request.getRequestURI(), remote);
      log.debug("execute request id '{}'", requestId);
      Thread requestBodyWriterThread = null;
      byte[] requestHeader = RequestHeaderModifier.fromRequest(request, remote.getHost(), remote.getPort(), requestHeaderModifier).toBytes();
      if (log.isTraceEnabled()) {
        log.trace("request header\n{}", HexDump
            .hexdump(requestHeader)
            .stream()
            .collect(Collectors.joining("\n")));
      }
      AtomicBoolean writerRun = new AtomicBoolean(true);
      try(OutputStream out = socket.getOutputStream()) {
        out.write(requestHeader);
        requestBodyWriterThread = new Thread(() -> {
            try {
              byte[] buf = new byte[8192];
              ServletInputStream in = request.getInputStream();
              long total = 0;
              while(writerRun.get()) {
                log.trace("reading request body which might block...");
                int read = in.read(buf);
                if(read == -1) {
                  log.debug("reached end of request body");
                  out.flush();
                  break;
                }
                total += read;
                log.trace("received '{}' bytes, total '{}', now writing to output stream which might block...", read, total);
                out.write(buf, 0, read);
                log.trace("done writing to output stream, continue");
              }
            } catch(Exception e) {
              log.error("failed to send request body to downstream", e);
            } finally {
              log.debug("exit");
            }
        }, Thread.currentThread().getName() + "-request-body-writer-" + requestId);
        requestBodyWriterThread.start();
        // TODO try to catch and ignore (log debug) broken pipes caused by clients closing the connection
        try(InputStream in = new BufferedInputStream(socket.getInputStream(), BUF_SIZE)) {
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
            // from memory this case is important because otherwise the reads below block and the response does
            // not proceed, so the client is waiting on this reverse proxy, the reverse proxy is waiting on the
            // downstream server and the downstream server thinks it is done so nothing happens, just waiting for timeouts
            // FIXME in case we get the 'hasResponseBody' wrong and we are waiting on a non arriving response body below
            // FIXME make sure there is some sort of timeout in the input stream (probably needs to be configurable too)
            log.debug("not sending response body, based on method or http response code from downstream server");
          } else if (contentLength != null) {
            log.debug("read content-length '{}' bytes from stream ...", contentLength);
            byte[] buf = new byte[BUF_SIZE];
            long cl = contentLength;
            long total = 0;
            for (;;) {
              int read = in.read(buf, 0, (int) Math.min(BUF_SIZE, cl));
              if(read < 0) {
                log.warn("reached end of stream before reading length announced in content-length header,"
                    + " read '{}', content-length '{}'", total, contentLength);
                if(!response.isCommitted()) {
                  response.setContentLength((int)total);
                }
                break;
              }
              total +=read;
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
                log.trace("written chunked response, length '{}'", chunk.length);
              }
            }
            log.debug("transfer encoding chunked, done");
          } else {
            // not so sure about this case
            // if the server does not set a content-length nor transfer-encoding chunked header,
            // there might not be a response body and we could end up waiting forever here.
//            byte[] buf = new byte[BUF_SIZE];
//            for (;;) {
//              int read = in.read(buf);
//              if (read == -1) {
//                respOut.flush();
//                break;
//              }
//              respOut.write(buf, 0, read);
//            }
          }
        }
      } finally {
        try {
          respOut.flush();
        } catch(Exception e) {
          log.debug("failed to flush response", e);
        }
        try {
          writerRun.set(false);
          if(requestBodyWriterThread != null) {
            requestBodyWriterThread.interrupt();
          }
        } catch(Exception e) {
          log.debug("failed to stop request body writer", e);
        }
      }
    } catch(Exception e) {
      throw new HttpException(e);
    } finally {
      log.debug("exit request '{}'", requestId);
    }
  }

  private static boolean hasResponseBody(HttpServletRequest req, HttpHeaders headers) {
    if (StringUtils.equalsIgnoreCase("head", req.getMethod())) {
      return false;
    }
    int sc = headers.statusCode();
    // https://stackoverflow.com/questions/8628725/comprehensive-list-of-http-status-codes-that-dont-include-a-response-body
    // according to this a 302 can have a body but usually doesn't:
    // https://stackoverflow.com/a/8059718
    return (sc >= 200) && (sc != 204) && (sc != 304);
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
      log.trace("next chunk size hex '{}', '{}' bytes", chunkSizeString, chunkSize);
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
