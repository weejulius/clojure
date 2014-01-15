package clojure.lang.exp;

import clojure.lang.Compiler.C;
import clojure.lang.Expr;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public interface MaybePrimitiveExpr extends Expr {
    public boolean canEmitPrimitive();

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen);
}
