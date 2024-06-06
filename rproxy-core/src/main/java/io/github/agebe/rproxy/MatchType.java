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

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.hrakaroo.glob.GlobPattern;
import com.hrakaroo.glob.MatchingEngine;

// FIXME consider URL encoding
public enum MatchType {
  GLOB(GlobMatcher::new),
  REGEX(RegExMatcher::new),
  EQUALS(EqualsMatcher::new),
  ALL(AllMatcher::new),
  ;

  public static abstract class AbstractMatcher implements Predicate<String> {

    protected String pattern;

    public AbstractMatcher(String pattern) {
      this.pattern = pattern;
    }

    @Override
    public String toString() {
      return pattern;
    }
  }

  public static class GlobMatcher extends AbstractMatcher {

    private MatchingEngine matcher;

    public GlobMatcher(String pattern) {
      super(pattern);
      this.matcher = GlobPattern.compile(pattern);
    }

    @Override
    public boolean test(String t) {
      return matcher.matches(t);
    }

  }

  private static class RegExMatcher extends AbstractMatcher {

    private Predicate<String> regexMatcher;

    public RegExMatcher(String pattern) {
      super(pattern);
      this.regexMatcher = Pattern.compile(pattern).asMatchPredicate();
    }

    @Override
    public boolean test(String t) {
      return regexMatcher.test(t);
    }
  }

  private static class EqualsMatcher extends AbstractMatcher {

    public EqualsMatcher(String pattern) {
      super(pattern);
    }

    @Override
    public boolean test(String t) {
      return StringUtils.equals(pattern, t);
    }
  }

  private static class AllMatcher extends AbstractMatcher {

    public AllMatcher(String pattern) {
      super(pattern);
    }

    @Override
    public boolean test(String t) {
      return true;
    }

  }

  private Function<String, Predicate<String>> newMatcher;

  private MatchType(Function<String, Predicate<String>> newMatcher) {
    this.newMatcher = newMatcher;
  }

  public Predicate<String> createMatcher(String pattern) {
    return newMatcher.apply(pattern);
  }
}
