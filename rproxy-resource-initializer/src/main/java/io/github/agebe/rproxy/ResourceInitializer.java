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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

public class ResourceInitializer implements ServletContainerInitializer {

  private static final Logger log = LoggerFactory.getLogger(ResourceInitializer.class);

  @Override
  public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
    HandlerRegister register = HandlerRegister.instance();
    List<Class<? extends HttpRequestHandler>> handlers = new Reflections().getSubTypesOf(HttpRequestHandler.class)
        .stream()
        .filter(cls -> !cls.isInterface())
        .filter(cls -> !Modifier.isAbstract(cls.getModifiers()))
        .sorted(Comparator.comparing(Class::getName))
        .toList();
    handlers.forEach(register::addHandler);
    log.info("registered '{}' proxy handler(s)", handlers.size());
  }

}
