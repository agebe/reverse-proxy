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

public class BadGatewayException extends ReverseProxyException {

  private static final long serialVersionUID = -3953959909303300898L;

  public BadGatewayException() {
    super();
  }

  public BadGatewayException(String messagePattern, Object... args) {
    super(messagePattern, args);
  }

  public BadGatewayException(String message, Throwable cause) {
    super(message, cause);
  }

  public BadGatewayException(String message) {
    super(message);
  }

  public BadGatewayException(Throwable cause) {
    super(cause);
  }

}
