package clojure.lang.exp;

import clojure.lang.Compiler.C;
import clojure.lang.Expr;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 *
 *
 * Created by jyu on 14-1-15.
 */
public interface AssignableExpr {
    Object evalAssign(Expr val);

    void emitAssign(C context, ObjExpr objx, GeneratorAdapter gen, Expr val);
}
