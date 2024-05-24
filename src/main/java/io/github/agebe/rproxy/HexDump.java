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

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;

public class HexDump {

  public static void hexdumptoLogger(Logger log, String msg, byte[] buf) {
    if(!log.isDebugEnabled()) {
      return;
    }
    if(msg != null) {
      log.debug(msg);
    }
    hexdump(buf, log::debug);
  }

  public static void hexdumpToSystemOut(String msg, byte[] buf) {
    if(msg != null) {
      System.out.println(msg);
    }
    hexdump(buf, System.out::println);
  }

  public static List<String> hexdump(byte[] buf) {
    List<String> dump = new ArrayList<>();
    hexdump(buf, dump::add);
    return dump;
  }

  public static String hexdumpToString(byte[] buf) {
    StringBuilder b = new StringBuilder();
    hexdump(buf, s -> b.append(s+"\n"));
    return b.toString();
  }

  public static void hexdumpToFile(String msg, byte[] buf, File destination) {
    try(PrintWriter writer = new PrintWriter(new FileWriter(destination))) {
      if(msg != null) {
        writer.println(msg);
      }
      hexdump(buf, writer::println);
    } catch(Exception e) {
      throw new RuntimeException("failed to hexdump to " + destination.getAbsolutePath(), e);
    }
  }

  private static boolean isPrintableChar(char c) {
    // ASCII only
    return (!Character.isISOControl(c)) && (c <= 0x80);
  }

  public static void hexdump(byte[] buf, Consumer<String> consumer) {
    int address = 0;
    while(address < buf.length) {
      String line = String.format("%08x  ", address);
      for(int i=0;i<16;i++) {
        if((address+i) < buf.length) {
          line += String.format("%02x ", buf[address+i]);
        } else {
          line += "   ";
        }
        if(i == 7) {
          line += ' ';
        }
      }
      line += " |";
      for(int i=0;i<16;i++) {
        if((address+i) < buf.length) {
          byte b = buf[address+i];
          int ub = b & 0xff;
          char c = Character.valueOf((char)ub);
          if(isPrintableChar(c)) {
            line += c;
          } else {
            line += '.';
          }
        } else {
          line += " ";
        }
      }
      line += "|";
      address += 16;
      consumer.accept(line);
    }
  }

  public static byte[] combine(List<byte[]> l) {
    int total = l.stream().mapToInt(buf -> buf.length).sum();
    byte[] combined = new byte[total];
    int current = 0;
    for(byte[] buf : l) {
      System.arraycopy(buf, 0, combined, current, buf.length);
      current += buf.length;
    }
    return combined;
  }

}
