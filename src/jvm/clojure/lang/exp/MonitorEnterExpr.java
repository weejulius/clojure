package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class MonitorEnterExpr extends UntypedExpr {
    final Expr target;

    public MonitorEnterExpr(Expr target) {
        this.target = target;
    }

    public Object eval() {
        throw new UnsupportedOperationException("Can't eval monitor-enter");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        target.emit(C.EXPRESSION, objx, gen);
        gen.monitorEnter();
        Compiler.NIL_EXPR.emit(context, objx, gen);
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object form) {
            return new MonitorEnterExpr(Compiler.analyze(C.EXPRESSION, RT.second(form)));
        }
    }
}
