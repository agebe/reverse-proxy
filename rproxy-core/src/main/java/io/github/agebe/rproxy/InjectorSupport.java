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

public class InjectorSupport {

  private static Injector injector = new Injector() {
    @Override
    public <T> T getInstance(Class<T> cls) {
      try {
        return cls.getConstructor().newInstance();
      } catch(Exception e) {
        throw new ReverseProxyException("failed to get instance of class '{}'", cls.getName());
      }
    }
  };

  public static Injector getInjector() {
    return injector;
  }

  public static void setInjector(Injector injector) {
    InjectorSupport.injector = injector;
  }

}
