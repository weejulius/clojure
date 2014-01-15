package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/
public class MetaExpr implements Expr {
    public final Expr expr;
    public final Expr meta;
    final static Type   IOBJ_TYPE      = Type.getType(IObj.class);
    final static Method withMetaMethod = Method.getMethod(
            "clojure.lang.IObj withMeta(clojure.lang.IPersistentMap)");


    public MetaExpr(Expr expr, Expr meta) {
        this.expr = expr;
        this.meta = meta;
    }

    public Object eval() {
        return ((IObj) expr.eval()).withMeta((IPersistentMap) meta.eval());
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        expr.emit(C.EXPRESSION, objx, gen);
        gen.checkCast(IOBJ_TYPE);
        meta.emit(C.EXPRESSION, objx, gen);
        gen.checkCast(Compiler.IPERSISTENTMAP_TYPE);
        gen.invokeInterface(IOBJ_TYPE, withMetaMethod);
        if (context == C.STATEMENT) {
            gen.pop();
        }
    }

    public boolean hasJavaClass() {
        return expr.hasJavaClass();
    }

    public Class getJavaClass() {
        return expr.getJavaClass();
    }
}
