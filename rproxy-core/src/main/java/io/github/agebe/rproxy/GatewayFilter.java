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
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class GatewayFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(GatewayFilter.class);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest)request;
    HttpServletResponse resp = (HttpServletResponse)response;
    try {
      // only 1 handler instance per class and request
      // this is just in case you have multiple overlapping matchers on a single handler
      // not sure if this is required
      Map<Class<? extends HttpRequestHandler>, HttpRequestHandler> cache = new HashMap<>();
      for(Handler handler : HandlerRegister.instance().getHandlers(req)) {
        HttpRequestHandler h = cache.computeIfAbsent(handler.handlerCls(),
            k -> InjectorSupport.getInjector().getInstance(k));
        log.debug("request '%s' matched '%s', executing handler '%s' ...",
            req.getRequestURI(),
            handler.matcher(),
            handler.handlerCls().getName());
        RequestStatus rs = h.handle(req, resp);
        if(RequestStatus.COMPLETED.equals(rs)) {
          return;
        } else if(RequestStatus.CONTINUE.equals(rs)) {
          continue;
        } else {
          log.error("unknown status '{}'", rs);
          resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
      }
    } catch(Exception e) {
      log.error("failed to process request", e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    // if no handler has taken care of this request continue with the filter chain
    chain.doFilter(request, response);
  }

}
