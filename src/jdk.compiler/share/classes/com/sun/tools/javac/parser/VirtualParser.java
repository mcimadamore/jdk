/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.parser.Tokens.Token;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCErroneous;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.Error;
import com.sun.tools.javac.util.JCDiagnostic.LintWarning;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.DeferredDiagnosticHandler;
import com.sun.tools.javac.util.Position.LineMap;

import javax.tools.Diagnostic.Kind;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The virtual parser allows for speculative parsing while not commiting to
 * consuming tokens unless the speculation is successful.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class VirtualParser extends JavacParser {

    Log.DeferredDiagnosticHandler deferredDiagnosticHandler;
    final JavacParser parentParser;

    private VirtualParser(JavacParser parser) {
        super(parser, new VirtualScanner(parser.S));
        deferredDiagnosticHandler = log.new DeferredDiagnosticHandler();
        this.parentParser = parser;
    }

//    public VirtualParser(JavacParser parser, boolean alt) {
//        super(parser, new VirtualScanner(parser.S));
//        //deferredDiagnosticHandler = log.new DeferredDiagnosticHandler();
//    }

    /**
     * Scanner that does token lookahead and throws AssertionErrors if an error
     * occurs.
     */
    public static class VirtualScanner implements Lexer {
        /** Parent scanner.
         */
        Lexer S;

        /** Token offset from where parent scanner branched.
         */
        int offset = 0;

        /** The token, set by nextToken().
         */
        private Token token;

        /** The previous token, set by nextToken().
         */
        private Token prevToken;

        /**
         * Return the position where a lexical error occurred;
         */
        int errPos;

        public VirtualScanner(Lexer s) {
            while (s instanceof VirtualScanner virtualScanner) {
                s = virtualScanner.S;
                offset += virtualScanner.offset;
            }
            S = s;
            token = s.token();
            prevToken = S.prevToken();
        }

        @Override
        public void nextToken() {
            prevToken = token;
            offset++;
            token = token();
        }

        @Override
        public Token token() {
            return token(0);
        }

        @Override
        public Token token(int lookahead) {
            return S.token(offset + lookahead);
        }

        @Override
        public Token prevToken() {
            return prevToken;
        }

        @Override
        public void setPrevToken(Token prevToken) {
            this.prevToken = prevToken;
        }

        @Override
        public Token split() {
            Token[] splitTokens = token.split(((Scanner)S).tokens);
            prevToken = splitTokens[0];
            token = splitTokens[1];
            return token;
        }

        @Override
        public Queue<Tokens.Comment> getDocComments() {
            return S.getDocComments();
        }

        @Override
        public int errPos() {
            return errPos;
        }

        @Override
        public void errPos(int pos) {
            errPos = pos;
        }

        @Override
        public LineMap getLineMap() {
            return S.getLineMap();
        }
    }

    private void commit() {
        // flush diagnostics and make sure that the err pos is propagated to the outer lexer
        deferredDiagnosticHandler.reportDeferredDiagnostics();
        // set last error pos
        parentParser.S.errPos(S.errPos());
        // advance scanner to current position
        for (int i = 0 ; i < ((VirtualScanner)S).offset ; i++) {
            parentParser.nextToken();
        }
        Assert.check(parentParser.token.pos == token.pos);
        Assert.check(parentParser.S.prevToken().pos == S.prevToken().pos);
        // merge end pos table entries
        endPosTable.dupTo(parentParser.endPosTable);
    }

    /**
     * Attempts a parse action and returns true if successful or false if
     * a parse error is thrown.
     *
     * @param parser        parent parser
     * @param parserAction  function that takes a parser and invokes a method on that parser
     *
     * @return true if successful
     */
    public static <R> Result<R> tryParse(JavacParser parser, Function<JavacParser, R> parserAction) {
        VirtualParser virtualParser = new VirtualParser(parser);
        try {
            return virtualParser.new Result<>(parserAction.apply(virtualParser));
        } catch (AssertionError ex) {
            return virtualParser.new Result<>(null);
        } finally {
            virtualParser.log.popDiagnosticHandler(virtualParser.deferredDiagnosticHandler);
        }
    }

    /**
     * The result of a trial parser run. A result is parameterized in the result type of the function
     * passed to {@link #tryParse(JavacParser, Function)}. The result contains detailed information
     * about any diagnostic that has been generated during parsing, as well as the result of the parse
     * action. Clients can inspect, without committing, to the results of a trial parser run using
     * the {@link #test(Predicate)} method. Alternatively, they can <em>commit</em> to a specific
     * trial parser run using the {@link #getAndCommit()} ()} method. When this method is called,
     * any diagnostic that has been generated during the trial parser run will be emitted.
     * @param <X> the result type
     */
    public class Result<X> {
        private final X x;

        private Result(X x) {
            this.x = x;
        }

        /**
         * {@return {@code true}, if the trial parser run completed normally}
         */
        public boolean isPresent() {
            return x != null;
        }

        /**
         * {@return {@code true}, if the trial parser run generated some error diagnostics}
         */
        public boolean hasErrors() {
            return deferredDiagnosticHandler.getDiagnostics().stream()
                    .anyMatch(d -> d.getKind() == Kind.ERROR);
        }

        /**
         * {@return {@code true}, if the trial parser run generated some warning diagnostics}
         */
        public boolean hasWarnings() {
            return deferredDiagnosticHandler.getDiagnostics().stream()
                    .anyMatch(d -> d.getKind() == Kind.WARNING);
        }

        /**
         * {@return {@code true} if this result indicates that the trial parser run completed
         * normally, and if the result value matches the provided predicate.
         * <p>
         * Clients should refrain from holding onto the result value passed to the provided predicate
         * (e.g. saving it in a field for further use outside the scope of the trial parser run). In such
         * cases they should use {@link #getAndCommit()} instead.
         *
         * @param predicate a predicate used to the the result value associated with a trial parser run
         */
        public boolean test(Predicate<X> predicate) {
            return isPresent() && predicate.test(x);
        }

        /**
         * {@return the result value, if present, of the trial parser action}
         */
        public X getAndCommit() {
            Objects.requireNonNull(x);
            commit();
            return x;
        }
    }
}
