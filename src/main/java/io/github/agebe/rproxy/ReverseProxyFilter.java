//package com.gridqube.controller.http;
//
//import java.io.BufferedInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.net.Socket;
//import java.net.URL;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//import java.util.stream.Collectors;
//
//import javax.inject.Singleton;
//import javax.net.ssl.SSLContext;
//import javax.net.ssl.SSLSocket;
//import javax.net.ssl.SSLSocketFactory;
//import javax.net.ssl.TrustManager;
//import javax.servlet.Filter;
//import javax.servlet.FilterChain;
//import javax.servlet.FilterConfig;
//import javax.servlet.ServletException;
//import javax.servlet.ServletRequest;
//import javax.servlet.ServletResponse;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//
//import org.apache.commons.lang3.StringUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import com.gridqube.controller.ControllerException;
//import com.gridqube.guice.Guice;
//import com.gridqube.security.Session;
//import com.gridqube.utils.HexDump;
//import com.gridqube.utils.ObjectUtils;
//
//@Singleton
//// https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers
//public class ReverseProxyFilter implements Filter {
//
//  private static final Logger log = LoggerFactory.getLogger(ReverseProxyFilter.class);
//
//  private static final int BUF_SIZE = 64 * 1024;
//
//  public static record RemoteSession(
//      URL baseUrl,
//      String sessionId,
//      String rId,
//      // controller location (for non-root deployments)
//      String cl) {
//
//    public String getHost() {
//      return baseUrl.getHost();
//    }
//  }
//
//  // maps controller sessions to remote instance sessions
//  private Map<String, RemoteSession> sessions = new HashMap<>();
//
//  private String contextPath;
//
//  private boolean isWebsocket(HttpServletRequest req) {
//    return req.getHeader("Sec-WebSocket-Key") != null;
//  }
//
//  @Override
//  public void init(FilterConfig filterConfig) throws ServletException {
//    filterConfig.getServletContext().getContextPath();
//    this.contextPath = filterConfig.getServletContext().getContextPath();
//  }
//
//  @Override
//  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
//      throws IOException, ServletException {
//    Session session = Guice.injector().getInstance(Session.class);
//    log.trace("req '{}', session '{}'", ((HttpServletRequest) request).getRequestURI(), session);
//    if (isReverseProxySession(session) && isNotExcludedRequest(request)) {
//      if (isWebsocket((HttpServletRequest) request)) {
//        // this reverse proxy does not support websocket currently.
//        // it works on the way to the backend but not on the way back to the user-agent.
//        // TODO: add support for reverse proxy websockets in the websocket module by
//        // forwarding events.
//        // this solution would be specific to the /events/websoc event websocket.
//        // send http 501 Not Implemented
//        ((HttpServletResponse) response).sendError(501);
//      } else {
//        doReverseProxy(session, (HttpServletRequest) request, (HttpServletResponse) response, chain);
//      }
//    } else {
//      chain.doFilter(request, response);
//    }
//  }
//
//  private boolean isPost(HttpServletRequest req) {
//    return "POST".equalsIgnoreCase(req.getMethod());
//  }
//
//  private boolean isGet(HttpServletRequest req) {
//    return "GET".equalsIgnoreCase(req.getMethod());
//  }
//
//  private boolean isTransferEncodingChunked(HttpHeaders headers) {
//    List<String> encodings = headers.getHeaders("transfer-encoding");
//    if (encodings != null) {
//      for (String s : encodings) {
//        if (StringUtils.containsIgnoreCase(s, "chunked")) {
//          return true;
//        }
//      }
//    }
//    return false;
//  }
//
//  private byte[] getRequestBody(HttpServletRequest req) {
//    try {
//      return req.getInputStream().readAllBytes();
//    } catch (Exception e) {
//      return null;
//    }
//  }
//
//  private int getChunkSize(InputStream in) {
//    byte[] chunkSizeBytes = HttpUtils.nextChunkSize(in);
//    if ((chunkSizeBytes == null) || chunkSizeBytes.length == 0) {
//      throw new HttpException("failed to read next chunk size from stream");
//    }
//    String chunkSizeString = new String(chunkSizeBytes);
//    try {
//      int chunkSize = Integer.parseInt(chunkSizeString, 16);
//      log.debug("next chunk size hex '{}', '{}' bytes", chunkSizeString, chunkSize);
//      return chunkSize;
//    } catch (Exception e) {
//      throw new HttpException("failed to convert hex chunk size '{}' to decimal", chunkSizeString);
//    }
//  }
//
//  private boolean isDisconnect(HttpServletRequest req, RemoteSession remoteSession) {
//    if (isPost(req) && "/signout".equalsIgnoreCase(req.getPathInfo())) {
//      log.debug("disconnect detected (signout)");
//      return true;
//    } else if (isGet(req) && (contextPath + "/").equals(req.getRequestURI())
//        && !remoteSession.rId.equals(req.getParameter("rid"))) {
//      log.debug("disconnect detected");
//      return true;
//    }
//    return false;
//  }
//
//  private boolean hasResponseBody(HttpServletRequest req, HttpHeaders headers) {
//    if (StringUtils.equalsIgnoreCase("head", req.getMethod())) {
//      return false;
//    }
//    int sc = headers.statusCode();
//    // https://stackoverflow.com/questions/8628725/comprehensive-list-of-http-status-codes-that-dont-include-a-response-body
//    return (sc >= 200) && (sc != 204) && (sc != 304);
//  }
//
//  private void doReverseProxy(
//      Session session,
//      HttpServletRequest req,
//      HttpServletResponse resp,
//      FilterChain chain) throws IOException, ServletException {
//    RemoteSession remote = getRemoteSession(session);
//    log.debug("session '{}', remote '{}', path '{}'", session, remote, req.getPathInfo());
//    if (isDisconnect(req, remote)) {
//      log.info("reverse proxy disconnect '{}'", remote.baseUrl);
//      removeRemoteSession(session);
//      // TODO send http signout to remote server
//      if (isGet(req) && (StringUtils.defaultString(contextPath) + "/").equals(req.getRequestURI())) {
//        log.trace("disconnect");
//        chain.doFilter(req, resp);
//      } else {
//        log.trace("disconnect, redirect '{}'", StringUtils.defaultString(contextPath) + "/");
//        // the signout ajax call won't follow the redirect
//        // but the back url should solve this problem and this is handled in the
//        // front-end
//        resp.sendRedirect(StringUtils.defaultString(contextPath) + "/");
//      }
//    } else {
//      byte[] requestBody = getRequestBody(req);
//      // why not use a http library to talk to the backend?
//      // can't remember why I implemented http/1.1 below.
//      // probably because I didn't want to translate incoming http headers to methods
//      // calls on the http library
//      // but how much work is this?
//      try (Socket socket = getSocket(remote)) {
//        byte[] requestHeader = HttpHeaderBuilder.create(
//            req,
//            session.getSessionId(),
//            contextPath,
//            remote);
//        if (log.isTraceEnabled()) {
//          log.trace("request header\n{}", HexDump
//              .hexdump(requestHeader)
//              .stream()
//              .collect(Collectors.joining("\n")));
//        }
//        OutputStream respOut = resp.getOutputStream();
//        try (OutputStream out = socket.getOutputStream()) {
//          out.write(requestHeader);
//          if (requestBody != null) {
//            out.write(requestBody);
//          }
//          out.flush();
//          try (InputStream in = new BufferedInputStream(socket.getInputStream(), BUF_SIZE)) {
//            HeaderParser parser = new HeaderParser(in);
//            HttpHeaders headers = parser.parse();
//            log.debug("http headers '{}'", headers);
//            if (log.isTraceEnabled()) {
//              log.trace("header bytes\n{}", HexDump
//                  .hexdump(headers.bytes())
//                  .stream()
//                  .collect(Collectors.joining("\n")));
//            }
//            setResponseHeaders(resp, headers);
//            Long contentLength = ObjectUtils.asLong(headers.getHeader("Content-Length"));
//            if (!hasResponseBody(req, headers)) {
//              return;
//            } else if (contentLength != null) {
//              log.debug("read content-length '{}' bytes from stream ...", contentLength);
//              byte[] buf = new byte[BUF_SIZE];
//              long cl = contentLength;
//              for (;;) {
//                int read = in.read(buf, 0, (int) Math.min(BUF_SIZE, cl));
//                respOut.write(buf, 0, read);
//                cl -= read;
//                log.trace("written '{}' bytes, '{}' bytes to go", read, cl);
//                if (cl <= 0) {
//                  log.trace("reached content-length of '{}' bytes, break", contentLength);
//                  break;
//                }
//              }
//            } else if (isTransferEncodingChunked(headers)) {
//              log.debug("transfer encoding chunked");
//              // do not write the http chunked protocol, let tomcat figure this out
//              for (;;) {
//                int chunkSize = getChunkSize(in);
//                byte[] chunk = HttpUtils.nextChunk(in, chunkSize);
//                if (chunkSize == 0) {
//                  break;
//                }
//                if (chunk != null) {
//                  respOut.write(chunk);
//                  log.debug("written chunked response, length '{}'", chunk.length);
//                }
//              }
//              log.debug("transfer encoding chunked, done");
//            } else {
//              byte[] buf = new byte[BUF_SIZE];
//              for (;;) {
//                int read = in.read(buf);
//                if (read == -1) {
//                  respOut.flush();
//                  break;
//                }
//                respOut.write(buf, 0, read);
//              }
//            }
//          }
//        }
//      }
//    }
//  }
//
//  private void setResponseHeaders(HttpServletResponse resp, HttpHeaders headers) {
//    log.debug("set response status '{}'", headers.statusCode());
//    resp.setStatus(headers.statusCode());
//    headers.headers().forEach((k, l) -> {
//      l.forEach(v -> {
//        // ignore transfer encoding chunked as we don't control the connection to the
//        // user-agent,
//        // tomcat handles this. Not sure about the other transfer encoding values though
//        // (compress, deflate, gzip)
//        // FIXME if there is a location header for redirects this needs to be mapped to
//        // the controller context path
//        if (StringUtils.equalsIgnoreCase("Transfer-Encoding", k)) {
//          log.debug("ignore header '{}', value '{}'", k, v);
//        } else {
//          log.debug("set response header '{}', value '{}'", k, v);
//          resp.addHeader(k, v);
//        }
//      });
//    });
//  }
//
//  public synchronized RemoteSession startRemoteSession(Session session, URL baseUrl, String remoteSessionId) {
//    if (session == null) {
//      throw new ControllerException("session is null");
//    }
//    if (baseUrl == null) {
//      throw new ControllerException("base url is null");
//    }
//    if (remoteSessionId == null) {
//      throw new ControllerException("remote session id is null");
//    }
//    RemoteSession rs = new RemoteSession(
//        baseUrl,
//        remoteSessionId,
//        UUID.randomUUID().toString(),
//        StringUtils.defaultIfBlank(contextPath, "/"));
//    sessions.put(session.getSessionId(), rs);
//    log.info("instance connect, started remote session '{}', remote session id '{}', rId '{}'", baseUrl, rs.sessionId,
//        rs.rId);
//    return rs;
//  }
//
//  public synchronized void disconnectRemoteSession(Session session) {
//    log.debug("disconnect remote session for '{}'", session);
//    removeRemoteSession(session);
//  }
//
//  private boolean isNotExcludedRequest(ServletRequest request) {
//    return !isExcludedRequest(request);
//  }
//
//  private boolean isExcludedRequest(ServletRequest request) {
//    if (request instanceof HttpServletRequest req) {
//      String uri = req.getRequestURI();
//      // all request to /managed-instance/ and /api/managed-instance/ are handled by
//      // the controller so never forward them.
//      // this allows the controller to serve the iframe and to handle the disconnect
//      // call
//      return StringUtils.startsWith(uri, "/managed-instance/") || StringUtils.startsWith(uri, "/api/managed-instance/");
//    } else {
//      return false;
//    }
//  }
//
//  private synchronized void removeRemoteSession(Session session) {
//    sessions.remove(session.getSessionId());
//  }
//
//  private synchronized RemoteSession getRemoteSession(Session session) {
//    if (session == null) {
//      return null;
//    }
//    return sessions.get(session.getSessionId());
//  }
//
//  private boolean isReverseProxySession(Session session) {
//    return getRemoteSession(session) != null;
//  }
//
//  private Socket getSocket(RemoteSession remote) {
//    try {
//      URL baseUrl = remote.baseUrl();
//      String protocol = baseUrl.getProtocol();
//      String host = baseUrl.getHost();
//      int port = baseUrl.getPort();
//      if ("https".equalsIgnoreCase(protocol)) {
//        SSLContext sc = SSLContext.getInstance("TLS");
//        sc.init(null, new TrustManager[] { new X509AllTrustManager() }, null);
//        SSLSocketFactory ssf = sc.getSocketFactory();
//        SSLSocket s = (SSLSocket) ssf.createSocket(host, (port == -1 ? 443 : port));
//        s.setSoTimeout(30000);
//        s.setKeepAlive(false);
//        s.startHandshake();
//        return s;
//      } else if ("http".equalsIgnoreCase(protocol)) {
//        Socket s = new Socket(host, (port == -1 ? 80 : port));
//        return s;
//      } else {
//        throw new ControllerException("failed to open socket, protocol in '{}' not supported", remote.baseUrl());
//      }
//    } catch (Exception e) {
//      throw new ControllerException("failed to open socket to '{}'", remote.baseUrl(), e);
//    }
//  }
//
//}
