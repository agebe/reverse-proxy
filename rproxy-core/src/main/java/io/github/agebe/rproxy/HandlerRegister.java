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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;

public class HandlerRegister {

  private static final Logger log = LoggerFactory.getLogger(HandlerRegister.class);

  private static final HandlerRegister INSTANCE = new HandlerRegister();

  private List<Handler> handlers = new ArrayList<>();

  private HandlerRegister() {
    super();
  }

  public void addHandler(Class<? extends HttpRequestHandler> handlerCls) {
    if(handlerCls.isInterface()) {
      throw new ReverseProxyException("can not register handler interface '%s'".formatted(handlerCls.getName()));
    }
    if(Modifier.isAbstract(handlerCls.getModifiers())) {
      throw new ReverseProxyException("can not register abstract handler class '%s'".formatted(handlerCls.getName()));
    }
    handlers.addAll(Stream.of(handlerCls)
        .flatMap(this::allPaths)
        .peek(h -> log.info("register handler '{}', path matcher '{}'", h.handlerCls(), h.matcher()))
        .toList());
  }

  public List<Handler> getHandlers(HttpServletRequest req) {
    return handlers.stream()
        .filter(handler -> handler.matcher().test(req.getRequestURI()))
        .toList();
  }

  private Stream<Handler> allPaths(Class<? extends HttpRequestHandler> handlerCls) {
    ProxyPath[] paths = handlerCls.getAnnotationsByType(ProxyPath.class);
    if(paths.length == 0) {
      // if the handler does not have a proxy path annotation is will handle all incoming calls
      return Stream.of(new Handler(MatchType.ALL.createMatcher(null), handlerCls));
    } else {
      return Arrays.stream(paths)
          .map(path -> new Handler(createMatcher(path), handlerCls));
    }
  }

  private Predicate<String> createMatcher(ProxyPath path) {
    return path.type().createMatcher(pathWithLeadingSlash(path.value()));
  }

  private String pathWithLeadingSlash(String path) {
    return path.startsWith("/")?path:"/"+path;
  }

  public static HandlerRegister instance() {
    return INSTANCE;
  }

}
