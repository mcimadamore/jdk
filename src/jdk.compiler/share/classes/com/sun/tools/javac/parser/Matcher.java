/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.parser;

import com.sun.tools.javac.parser.Matcher.Result.Success;

import java.util.function.Supplier;

/**
 * A matcher is used to match pieces of lexer input. Upon matching, a matcher
 * returns either a success, with an indication of how many tokens have been looked at,
 * or a failure.
 * <p>
 * Each token is implicitly a matcher, which matches exactly one token, in case when
 * the lexer input is that token.
 * <p>
 * Matchers can be combined, using <em>combinators</em>. For instance, given two token matches
 * {@code T1} and {@code T2} it is possible to create a matcher that first matches {@code T1}, then
 * (if successful) also matches {@code T2} -- this is the {@linkplain #and(Matcher) and matcher}.
 */
public interface Matcher {
    Result match(Lexer lexer, int lookahead);
    default boolean match(Lexer lexer) {
        return match(lexer, 0) instanceof Success;
    }

    default Matcher or(Matcher other) {
        return (lexer, lookahead) ->
                match(lexer, lookahead)
                        .or(() -> other.match(lexer, lookahead));
    }

    default Matcher and(Matcher other) {
        return (lexer, lookahead) ->
                match(lexer, lookahead)
                        .and(() -> other.match(lexer, lookahead));
    }

    sealed interface Result {
        Result or(Supplier<Result> other);
        Result and(Supplier<Result> other);

        Failure ERROR = new Failure();
        static Success ofSuccess(int readTokens) {
            return new Success(readTokens);
        }
        record Success(int readTokens) implements Result {
            @Override
            public Result or(Supplier<Result> other) {
                return this;
            }
            @Override
            public Result and(Supplier<Result> other) {
                return other.get() instanceof Success(int readTokensOther) ?
                        new Success(readTokens + readTokensOther) :
                        ERROR;
            }
        }
        record Failure() implements Result {
            @Override
            public Result or(Supplier<Result> other) {
                return other.get();
            }
            @Override
            public Result and(Supplier<Result> other) {
                return this;
            }
        }
    }
}
