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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public abstract class AbstractHttpRequestHandler implements HttpRequestHandler {

  public RequestStatus deny(HttpServletResponse response) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    return RequestStatus.COMPLETED;
  }

  public RequestStatus forwardStreamResult(
      String baseUrl,
      HttpServletRequest request,
      HttpServletResponse response) {
    ReverseProxy.forwardRequestStreamResult(baseUrl, request, response, null);
    return RequestStatus.COMPLETED;
  }

  public RequestStatus forwardStreamResult(
      String baseUrl,
      HttpServletRequest request,
      HttpServletResponse response,
      ResponseHeaderModifier headerModifier) {
    ReverseProxy.forwardRequestStreamResult(baseUrl, request, response, headerModifier);
    return RequestStatus.COMPLETED;
  }

}
