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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class EchoServer {

  private HttpServer server;

  private class RedirectHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange he) throws IOException {
      System.out.println("redirect, handle request '%s' '%s'".formatted(he.getRequestMethod(), he.getRequestURI()));
      String body = "redirected";
      he.getResponseHeaders().add("Location", "/test");
      he.sendResponseHeaders(302, body.getBytes().length);
      he.sendResponseHeaders(302, -1);
      he.getResponseBody().write(body.getBytes());
      he.getResponseBody().close();
    }
  }

  private class EchoHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange he) throws IOException {
      System.out.println("handle request '%s' '%s'".formatted(he.getRequestMethod(), he.getRequestURI()));
      he.sendResponseHeaders(200, 0);
      InputStream in = he.getRequestBody();
      long total = 0;
      try(OutputStream out = he.getResponseBody()) {
        byte[] buf = new byte[64*1024];
        for(;;) {
          System.out.println("reading, which might block...");
          int read = in.read(buf);
          if(read == -1) {
            break;
          }
          total+=read;
          System.out.println("receiving..., received '%s', total '%s'".formatted(read, total));
          System.out.println("writing, which might block...");
          out.write(buf, 0, read);
          System.out.println("echo..., written back to output stream".formatted(read, total));
        }
        if(total == 0) {
          out.write("request received".getBytes());
        }
      }
      System.out.println("request completed, received '%s' bytes".formatted(total));
    }
  }

  private void run(String[] args) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 3000), 0);
    server.createContext("/redirect", new RedirectHandler());
    server.createContext("/", new EchoHandler());
    server.setExecutor(null); // creates a default executor
    server.start();
  }

  public static void main(String[] args) throws Exception {
    System.out.println("start echo server");
    new EchoServer().run(args);
  }

}
