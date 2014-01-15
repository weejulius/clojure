package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class MonitorExitExpr extends UntypedExpr {
    final Expr target;

    public MonitorExitExpr(Expr target) {
        this.target = target;
    }

    public Object eval() {
        throw new UnsupportedOperationException("Can't eval monitor-exit");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        target.emit(C.EXPRESSION, objx, gen);
        gen.monitorExit();
        Compiler.NIL_EXPR.emit(context, objx, gen);
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object form) {
            return new MonitorExitExpr(Compiler.analyze(C.EXPRESSION, RT.second(form)));
        }
    }

}
