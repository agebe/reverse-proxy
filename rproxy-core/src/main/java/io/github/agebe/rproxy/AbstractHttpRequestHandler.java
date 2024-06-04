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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Function;

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
    return forwardStreamResult(baseUrl, request, response, null, null);
  }

  private OutputStream getResponseOutputStream(HttpServletResponse response) {
    try {
      return response.getOutputStream();
    } catch(IOException e) {
      throw new ReverseProxyException("failed on get response output stream", e);
    }
  }

  public RequestStatus forwardStreamResult(
      String baseUrl,
      HttpServletRequest request,
      HttpServletResponse response,
      RequestHeaderModifier requestHeaderModifier,
      ResponseHeaderModifier responseHeaderModifier) {
    return forwardStreamResult(
        baseUrl,
        request,
        response,
        requestHeaderModifier,
        responseHeaderModifier,
        getResponseOutputStream(response));
  }

  public RequestStatus forwardStreamResult(
      String baseUrl,
      HttpServletRequest request,
      HttpServletResponse response,
      RequestHeaderModifier requestHeaderModifier,
      ResponseHeaderModifier responseHeaderModifier,
      OutputStream responseOutputStream) {
    try {
      ReverseProxy.forwardRequestStreamResult(
          baseUrl,
          request,
          response,
          requestHeaderModifier,
          responseHeaderModifier,
          responseOutputStream);
      return RequestStatus.COMPLETED;
    } catch(Exception e) {
      throw new ReverseProxyException("failed on forward", e);
    }
  }

  public RequestStatus forwardModifyResult(
      String baseUrl,
      HttpServletRequest request,
      HttpServletResponse response,
      RequestHeaderModifier requestHeaderModifier,
      ResponseHeaderModifier responseHeaderModifier,
      Function<byte[], byte[]> responseContentModifier) {
    if(responseContentModifier != null) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      forwardStreamResult(
          baseUrl,
          request,
          response,
          requestHeaderModifier,
          responseHeaderModifier,
          out);
      byte[] modified = responseContentModifier.apply(out.toByteArray());
      if(modified != null) {
        response.setHeader("Content-Length", Integer.toString(modified.length));
        try(OutputStream o = getResponseOutputStream(response)) {
          o.write(modified);
        } catch (IOException e) {
          throw new ReverseProxyException("failed on forward", e);
        }
      }
      return RequestStatus.COMPLETED;
    } else {
      return forwardStreamResult(baseUrl, request, response, requestHeaderModifier, responseHeaderModifier);
    }
  }

}
