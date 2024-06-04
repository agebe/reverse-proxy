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

import org.slf4j.helpers.MessageFormatter;

public class ReverseProxyException extends RuntimeException {

  private static final long serialVersionUID = 2521664048929495204L;

  public ReverseProxyException() {
    super();
  }

  public ReverseProxyException(String messagePattern, Object... args) {
    super(MessageFormatter.arrayFormat(messagePattern, args).getMessage(),
        (args[args.length-1] instanceof Throwable)?(Throwable)args[args.length-1]:null);
  }

  public ReverseProxyException(String message, Throwable cause) {
    super(message, cause);
  }

  public ReverseProxyException(String message) {
    super(message);
  }

  public ReverseProxyException(Throwable cause) {
    super(cause);
  }

}
