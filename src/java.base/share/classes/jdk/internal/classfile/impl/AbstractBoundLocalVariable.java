/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.lang.classfile.Label;
import java.lang.classfile.constantpool.Utf8Entry;

public class AbstractBoundLocalVariable
        extends AbstractElement implements Util.WritableLocalVariable {
    protected final CodeImpl code;
    protected final int offset;

    public AbstractBoundLocalVariable(CodeImpl code, int offset) {
        this.code = code;
        this.offset = offset;
    }

    protected int nameIndex() {
        return code.classReader.readU2(offset + 4);
    }

    private Utf8Entry nameEntry;
    private static final LazyUpdater<AbstractBoundLocalVariable, Utf8Entry> NAME_ENTRY = LazyUpdater.ofInstance(AbstractBoundLocalVariable.class, "nameEntry", Utf8Entry.class, java.lang.invoke.MethodHandles.lookup());

    public Utf8Entry name() {

        return NAME_ENTRY.getOrCompute(this, AbstractBoundLocalVariable::compute_name_43);

    }

    private Utf8Entry compute_name_43() {
        return code.constantPool().entryByIndex(nameIndex(), Utf8Entry.class);
    }

    protected int secondaryIndex() {
        return code.classReader.readU2(offset + 6);
    }

    private Utf8Entry secondaryEntry;
    private static final LazyUpdater<AbstractBoundLocalVariable, Utf8Entry> SECONDARY_ENTRY = LazyUpdater.ofInstance(AbstractBoundLocalVariable.class, "secondaryEntry", Utf8Entry.class, java.lang.invoke.MethodHandles.lookup());

    protected Utf8Entry secondaryEntry() {

        return SECONDARY_ENTRY.getOrCompute(this, AbstractBoundLocalVariable::compute_secondaryEntry_51);

    }

    private Utf8Entry compute_secondaryEntry_51() {
        return code.constantPool().entryByIndex(secondaryIndex(), Utf8Entry.class);
    }

    public Label startScope() {
        return code.getLabel(startPc());
    }

    public Label endScope() {
        return code.getLabel(startPc() + length());
    }

    public int startPc() {
        return code.classReader.readU2(offset);
    }

    public int length() {
        return code.classReader.readU2(offset+2);
    }

    public int slot() {
        return code.classReader.readU2(offset + 8);
    }

    @Override
    public boolean writeLocalTo(BufWriterImpl b) {
        var lc = b.labelContext();
        int startBci = lc.labelToBci(startScope());
        int endBci = lc.labelToBci(endScope());
        if (startBci == -1 || endBci == -1) {
            return false;
        }
        int length = endBci - startBci;
        b.writeU2U2(startBci, length);
        if (b.canWriteDirect(code.constantPool())) {
            b.writeU2U2(nameIndex(), secondaryIndex());
        }
        else {
            b.writeU2U2(b.cpIndex(name()), b.cpIndex(secondaryEntry()));
        }
        b.writeU2(slot());
        return true;
    }
}
