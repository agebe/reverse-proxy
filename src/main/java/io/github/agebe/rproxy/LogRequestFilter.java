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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterators;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

public class LogRequestFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(LogRequestFilter.class);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if(log.isTraceEnabled()) {
      HttpServletRequest req = (HttpServletRequest)request;
      log.trace("content length '{}'", req.getContentType());
      log.trace("content type '{}'", req.getContentType());
      log.trace("context path '{}'", req.getContextPath());
      log.trace("method '{}'", req.getMethod());
      log.trace("path info '{}'", req.getPathInfo());
      log.trace("path translated '{}'", req.getPathTranslated());
      log.trace("protocol '{}'", req.getProtocol());
      log.trace("query string '{}'", req.getQueryString());
      log.trace("request URI'{}'", req.getRequestURI());
      log.trace("scheme '{}'", req.getScheme());
      log.trace("servlet path '{}'", req.getServletPath());
      log.trace("isSecure '{}'", req.isSecure());
      stream(req.getHeaderNames())
      .sorted()
      .forEachOrdered(h -> {
        String values = stream(req.getHeaders(h)).collect(Collectors.joining(", "));
        log.trace("header '{}', values '{}'", h, values);
      });
      stream(req.getAttributeNames())
      .sorted()
      .forEachOrdered(attr -> {
        log.trace("attribute '{}', value '{}'", attr, req.getAttribute(attr));
      });
      stream(req.getParameterNames()).sorted().forEachOrdered(param -> {
        String values = Arrays.stream(req.getParameterValues(param)).collect(Collectors.joining(", "));
        log.trace("parameter '{}', values '{}'", param, values);
      });
    }
    chain.doFilter(request, response);
  }

  // from https://stackoverflow.com/a/33244012
  private static <T> Stream<T> stream(Enumeration<T> enumeration) {
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
        Iterators.forEnumeration(enumeration), Spliterator.ORDERED), false);
  }

}
