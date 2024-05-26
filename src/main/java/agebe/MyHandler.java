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
package agebe;

import java.util.LinkedHashMap;
import java.util.List;

import io.github.agebe.rproxy.AbstractHttpRequestHandler;
import io.github.agebe.rproxy.HttpHeaders;
import io.github.agebe.rproxy.HttpRequestHeader;
import io.github.agebe.rproxy.RequestStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MyHandler extends AbstractHttpRequestHandler {

  @Override
  public RequestStatus handle(HttpServletRequest request, HttpServletResponse response) {
    return forwardStreamResult("https://docker.gridqube.com", request, response, this::reqHeader, this::respHeader);
//    return forwardModifyResult("https://docker.gridqube.com", request, this::empty);
//    return deny(response);
  }

  private HttpRequestHeader reqHeader(HttpRequestHeader h) {
    var m = new LinkedHashMap<>(h.headers());
    m.put("X-Powered-By", List.of("Gateway API-Server"));
    return new HttpRequestHeader(h.method(), h.requestURI(), h.queryString(), m);
  }

  private HttpHeaders respHeader(HttpHeaders headers) {
    var h = new LinkedHashMap<>(headers.headers());
    // just examples to test
    h.put("X-Powered-By", List.of("Gateway API-Server"));
    h.remove("Docker-Distribution-Api-Version");
    return new HttpHeaders(headers.version(), headers.statusCode(), headers.status(), h);
  }

//  private byte[] empty(byte[] buf) {
//    return "".getBytes();
//  }

}
