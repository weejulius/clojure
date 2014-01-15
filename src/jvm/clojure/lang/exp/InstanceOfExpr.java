package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class InstanceOfExpr implements Expr, MaybePrimitiveExpr {
    Expr  expr;
    Class c;

    public InstanceOfExpr(Class c, Expr expr) {
        this.expr = expr;
        this.c = c;
    }

    public Object eval() {
        if (c.isInstance(expr.eval()))
            return RT.T;
        return RT.F;
    }

    public boolean canEmitPrimitive() {
        return true;
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        expr.emit(C.EXPRESSION, objx, gen);
        gen.instanceOf(Compiler.getType(c));
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        emitUnboxed(context, objx, gen);
        HostExpr.emitBoxReturn(objx, gen, Boolean.TYPE);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return Boolean.TYPE;
    }

}
