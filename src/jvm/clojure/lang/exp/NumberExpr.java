package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class NumberExpr extends LiteralExpr implements MaybePrimitiveExpr {
    final        Number n;
    public final int    id;

    public NumberExpr(Number n) {
        this.n = n;
        this.id = Compiler.registerConstant(n);
    }

    Object val() {
        return n;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        if (context != C.STATEMENT) {
            objx.emitConstant(gen, id);
//			emitUnboxed(context,objx,gen);
//			HostExpr.emitBoxReturn(objx,gen,getJavaClass());
        }
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        if (n instanceof Integer)
            return long.class;
        else if (n instanceof Double)
            return double.class;
        else if (n instanceof Long)
            return long.class;
        else
            throw new IllegalStateException("Unsupported Number type: " + n.getClass().getName());
    }

    public boolean canEmitPrimitive() {
        return true;
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        if (n instanceof Integer)
            gen.push(n.longValue());
        else if (n instanceof Double)
            gen.push(n.doubleValue());
        else if (n instanceof Long)
            gen.push(n.longValue());
    }

    static public Expr parse(Number form) {
        if (form instanceof Integer
                || form instanceof Double
                || form instanceof Long)
            return new NumberExpr(form);
        else
            return new ConstantExpr(form);
    }
}
