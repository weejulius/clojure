package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import clojure.lang.Compiler.PATHTYPE;
import clojure.lang.Compiler.PathNode;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class LocalBindingExpr implements Expr, MaybePrimitiveExpr, AssignableExpr {
    public final LocalBinding b;
    public final Symbol tag;

    public final PathNode clearPath;
    public final PathNode clearRoot;
    public boolean shouldClear = false;


    public LocalBindingExpr(LocalBinding b, Symbol tag) {
        if (b.getPrimitiveType() != null && tag != null)
            throw new UnsupportedOperationException("Can't type hint a primitive local");
        this.b = b;
        this.tag = tag;

        this.clearPath = (PathNode) Compiler.CLEAR_PATH.get();
        this.clearRoot = (PathNode) Compiler.CLEAR_ROOT.get();
        IPersistentCollection sites = (IPersistentCollection) RT.get(Compiler.CLEAR_SITES.get(), b);

        if (b.idx > 0) {
//            Object dummy;

            if (sites != null) {
                for (ISeq s = sites.seq(); s != null; s = s.next()) {
                    LocalBindingExpr o = (LocalBindingExpr) s.first();
                    PathNode common = Compiler.commonPath(clearPath, o.clearPath);
                    if (common != null && common.type == PATHTYPE.PATH)
                        o.shouldClear = false;
//                    else
//                        dummy = null;
                }
            }

            if (clearRoot == b.clearPathRoot) {
                this.shouldClear = true;
                sites = RT.conj(sites, this);
                Compiler.CLEAR_SITES.set(RT.assoc(Compiler.CLEAR_SITES.get(), b, sites));
            }
//            else
//                dummy = null;
        }
    }

    public Object eval() {
        throw new UnsupportedOperationException("Can't eval locals");
    }

    public boolean canEmitPrimitive() {
        return b.getPrimitiveType() != null;
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        objx.emitUnboxedLocal(gen, b);
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        if (context != C.STATEMENT)
            objx.emitLocal(gen, b, shouldClear);
    }

    public Object evalAssign(Expr val) {
        throw new UnsupportedOperationException("Can't eval locals");
    }

    public void emitAssign(C context, ObjExpr objx, GeneratorAdapter gen, Expr val) {
        objx.emitAssignLocal(gen, b, val);
        if (context != C.STATEMENT)
            objx.emitLocal(gen, b, false);
    }

    public boolean hasJavaClass() {
        return tag != null || b.hasJavaClass();
    }

    public Class getJavaClass() {
        if (tag != null)
            return HostExpr.tagToClass(tag);
        return b.getJavaClass();
    }


}
