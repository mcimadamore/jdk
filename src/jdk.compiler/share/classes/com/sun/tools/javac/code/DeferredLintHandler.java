/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.LintWarning;
import com.sun.tools.javac.util.Log;

/**
 * Holds pending {@link Lint} warnings until the {@lint Lint} instance associated with the containing
 * module, package, class, method, or variable declaration is known, so that {@link @SupressWarnings}
 * suppressions may be applied.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class DeferredLintHandler {

    protected static final Context.Key<DeferredLintHandler> deferredLintHandlerKey = new Context.Key<>();

    public static DeferredLintHandler instance(Context context) {
        DeferredLintHandler instance = context.get(deferredLintHandlerKey);
        if (instance == null)
            instance = new DeferredLintHandler(context);
        return instance;
    }

    /**
     * The {@link Log} singleton.
     */
    private final Log log;

    /**
     * The root {@link Lint} singleton.
     */
    private final Lint rootLint;

    /**
     * {@code SuppressWarnings}-supporting declarations (with lexical ranges) keyed by source file.
     */
    private final Map<JavaFileObject, List<DeclNode>> declNodesMap = new HashMap<>();

    /**
     * Pending reports waiting for flush() keyed by source file.
     */
    private final Map<JavaFileObject, List<PendingReport>> pendingReportsMap = new HashMap<>();

    @SuppressWarnings("this-escape")
    protected DeferredLintHandler(Context context) {
        context.put(deferredLintHandlerKey, this);
        log = Log.instance(context);
        rootLint = Lint.instance(context);
    }

// Parsing Stuff

    /**
     * Report finishing parsing a declaration that supports {@code @SuppressWarnings}.
     *
     * @param decl the newly parsed declaration
     * @param endPos the ending position of {@code decl} (exclusive)
     * @return the given {@code decl} (for fluent chaining)
     */
    public <T extends JCTree> T endDecl(T decl, int endPos) {

        // Basic sanity checks
        Assert.check(decl.getTag() == Tag.MODULEDEF
                  || decl.getTag() == Tag.PACKAGEDEF
                  || decl.getTag() == Tag.CLASSDEF
                  || decl.getTag() == Tag.METHODDEF
                  || decl.getTag() == Tag.VARDEF);

        // Create a new declaration node
        JavaFileObject sourceFile = log.currentSourceFile();
        List<DeclNode> declNodes = declNodesMap.computeIfAbsent(sourceFile, s -> new ArrayList<>());
        DeclNode declNode = new DeclNode(decl, endPos);

        // Verify our assumptions about declarations:
        //  1. If two declarations overlap, then one of them must nest within the other
        //  2. endDecl() is invoked in order of increasing declaration ending position
        if (!declNodes.isEmpty()) {
            DeclNode prevNode = declNodes.get(declNodes.size() - 1);
            Assert.check(declNode.endPos() >= prevNode.endPos());
            Assert.check(declNode.startPos() >= prevNode.endPos() || declNode.startPos() <= prevNode.startPos());
        }

        // Add the new node to the list associated with the current source file
        declNodes.add(declNode);
        return decl;
    }

// Reporting API

    /**An interface for deferred lint reporting - loggers passed to
     * {@link #report(LintLogger) } will be called when
     * {@link #flush(DiagnosticPosition) } is invoked.
     */
    public interface LintLogger {

        /**
         * Generate a warning if appropriate.
         *
         * @param lint the applicable lint configuration
         */
        void report(Lint lint);
    }

    /**
     * Report a warning subject to possible suppression by {@code @SuppressWarnings}.
     *
     * @param pos warning position
     * @param key warning key
     */
    public void report(DiagnosticPosition pos, LintWarning key) {
        this.report(pos, lint -> lint.logIfEnabled(pos, key));
    }

    /**
     * Report a warning subject to possible suppression by {@code @SuppressWarnings}.
     *
     * @param pos warning position
     * @param logger logging callback
     */
    public void report(DiagnosticPosition pos, LintLogger logger) {
        JavaFileObject sourceFile = log.currentSourceFile();
        Assert.check(sourceFile != null);
        pendingReportsMap.computeIfAbsent(sourceFile, s -> new ArrayList<>()).add(new PendingReport(pos, logger));
    }

// Warning Flush

    /**
     * Emit deferred warnings encompassed by the given declaration.
     *
     * @param decl module, package, class, method, or variable declaration
     * @param lint lint configuration corresponding to {@code decl}
     */
    public void flush(JCTree decl, Lint lint) {
        JavaFileObject sourceFile = log.currentSourceFile();
        flush(sourceFile, decl, lint);
    }

    /**
     * Emit "top level" deferred warnings not encompassed by any declarations.
     *
     * @param sourceFile source file
     */
    public void flush(JavaFileObject sourceFile) {
        flush(sourceFile, null, rootLint);

        // Install land mines: there should be no more activity relating to this source file
        declNodesMap.put(sourceFile, null);
        pendingReportsMap.put(sourceFile, null);
    }

    private void flush(JavaFileObject sourceFile, JCTree flushDecl, Lint lint) {

        // Get the DeclNodes and pending reports for this source file
        List<DeclNode> declNodes = declNodesMap.get(sourceFile);
        List<PendingReport> pendingReports = pendingReportsMap.get(sourceFile);
        Assert.check(sourceFile != null);
        Assert.check(declNodes != null);

        // Any reports to flush?
        if (pendingReports == null)
            return;

        // Flush matching reports
        for (Iterator<PendingReport> i = pendingReports.iterator(); i.hasNext(); ) {
            PendingReport pendingReport = i.next();
            if (findTree(declNodes, pendingReport.pos().getStartPosition()) == flushDecl) {
                pendingReport.logger().report(lint);
                i.remove();
            }
        }
    }

    // Map the source file position to the innermost containing declaration - TODO - make this faster
    private JCTree findTree(List<DeclNode> declNodes, int pos) {
        DeclNode bestMatch = null;
        for (DeclNode declNode : declNodes) {
            if (declNode.contains(pos) && (bestMatch == null || declNode.isContainedBy(bestMatch))) {
                bestMatch = declNode;
            }
        }
        return bestMatch != null ? bestMatch.decl() : null;
    }

// PendingReport

    // A warning report that is waiting to be flushed
    private record PendingReport(DiagnosticPosition pos, LintLogger logger) { }

// DeclNode

    // A declaration with starting (inclusive) and ending (exclusive) positions
    private record DeclNode(JCTree decl, int startPos, int endPos) {

        DeclNode(JCTree decl, int endPos) {
            this(decl, TreeInfo.getStartPos(decl), endPos);
        }

        boolean contains(int pos) {
            return pos == startPos() || (pos > startPos() && pos < endPos());
        }

        boolean isContainedBy(DeclNode that) {
            return that.startPos() <= this.startPos() && that.endPos() >= this.endPos();
        }
    }
}
