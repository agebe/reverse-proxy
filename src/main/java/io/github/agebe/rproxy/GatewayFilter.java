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
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class GatewayFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(GatewayFilter.class);

  private List<Class<? extends HttpRequestHandler>> handlers;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    handlers = new Reflections().getSubTypesOf(HttpRequestHandler.class)
        .stream()
        .filter(cls -> !cls.isInterface())
        .filter(cls -> !Modifier.isAbstract(cls.getModifiers()))
        .sorted(Comparator.comparing(Class::getName))
        .toList();
    log.info("handlers '{}'", handlers);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest)request;
    HttpServletResponse resp = (HttpServletResponse)response;
    for(HttpRequestHandler handler : getHandlers(req)) {
      RequestStatus rs = handler.handle(req, resp);
      if(RequestStatus.COMPLETED.equals(rs)) {
        return;
      } else if(RequestStatus.CONTINUE.equals(rs)) {
        continue;
      } else {
        throw new HttpException("unknown status '{}'", rs);
      }
    }
    // if no handler has taken care of this request continue with the filter chain
    chain.doFilter(request, response);
  }

  private List<HttpRequestHandler> getHandlers(HttpServletRequest req) {
    return handlers.stream()
        .map(cls -> (HttpRequestHandler)InjectorSupport.getInjector().getInstance(cls))
        .toList();
  }

}
