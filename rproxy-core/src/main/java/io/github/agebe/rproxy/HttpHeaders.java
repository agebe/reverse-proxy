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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

public record HttpHeaders(
    String version,
    int statusCode,
    String status,
    Map<String, List<String>> headers) {

  public Optional<Map.Entry<String, List<String>>> findHeader(String name) {
    return headers
        .entrySet()
        .stream()
        .filter(me -> StringUtils.equalsIgnoreCase(name, me.getKey()))
        .findFirst();
  }

  // returns the header only if it has a single value
  // returns null if the header is not in the map
  // throws exception if the header has multiple values
  public String getHeader(String name) {
    var h = findHeader(name);
    if(h.isPresent()) {
      List<String> l = h.get().getValue();
      if(l == null) {
        return null;
      } else if(l.size() == 1) {
        return l.get(0);
      } else {
        throw new ReverseProxyException("header '{}' is a multi value header ('{}')", name, l.size());
      }
    } else {
      return null;
    }
  }

  public List<String> getHeaders(String name) {
    return findHeader(name).map(me -> me.getValue()).orElse(null);
  }

}
