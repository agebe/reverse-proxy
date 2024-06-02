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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hrakaroo.glob.GlobPattern;
import com.hrakaroo.glob.MatchingEngine;

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

  private static record Handler(
      Predicate<String> matcher,
      Class<? extends HttpRequestHandler> handlerCls) {}

  private List<Handler> handlers;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    handlers = new Reflections().getSubTypesOf(HttpRequestHandler.class)
        .stream()
        .filter(cls -> !cls.isInterface())
        .filter(cls -> !Modifier.isAbstract(cls.getModifiers()))
        .sorted(Comparator.comparing(Class::getName))
        .flatMap(this::allPaths)
        .toList();
    log.info("handlers '{}'", handlers);
  }

  private Stream<Handler> allPaths(Class<? extends HttpRequestHandler> handlerCls) {
    ProxyPath[] paths = handlerCls.getAnnotationsByType(ProxyPath.class);
    if(paths.length == 0) {
      // if the handler does not have a proxy path annotation is will handle all incoming calls
      return Stream.of(new Handler(s -> true, handlerCls));
    } else {
      return Arrays.stream(paths)
          .map(path -> new Handler(createMatcher(path), handlerCls));
    }
  }

  private Predicate<String> createMatcher(ProxyPath path) {
    MatchType type = path.type();
    if(MatchType.GLOB.equals(type)) {
      MatchingEngine matcher = GlobPattern.compile(pathWithLeadingSlash(path.value()));
      return s -> matcher.matches(s);
    } else if(MatchType.REGEX.equals(type)) {
      return Pattern.compile(pathWithLeadingSlash(path.value())).asPredicate();
    } else {
      throw new HttpException("match type '%s' not implemented".formatted(type));
    }
  }

  private String pathWithLeadingSlash(String path) {
    return path.startsWith("/")?path:"/"+path;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest)request;
    HttpServletResponse resp = (HttpServletResponse)response;
    // only 1 handler instance per class and request
    // this is just in case you have multiple overlapping matchers on a single handler
    // not sure if this is required
    Map<Class<? extends HttpRequestHandler>, HttpRequestHandler> cache = new HashMap<>();
    for(Handler handler : getHandlers(req)) {
      HttpRequestHandler h = cache.computeIfAbsent(handler.handlerCls(),
          k -> InjectorSupport.getInjector().getInstance(k));
      RequestStatus rs = h.handle(req, resp);
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

  private List<Handler> getHandlers(HttpServletRequest req) {
    return handlers.stream()
        .filter(handler -> handler.matcher.test(req.getRequestURI()))
        .toList();
  }

}
