package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class ThrowExpr extends UntypedExpr {
    public final Expr excExpr;

    public ThrowExpr(Expr excExpr) {
        this.excExpr = excExpr;
    }


    public Object eval() {
        throw Util.runtimeException("Can't eval throw");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        excExpr.emit(C.EXPRESSION, objx, gen);
        gen.checkCast(Compiler.THROWABLE_TYPE);
        gen.throwException();
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object form) {
            if (context == C.EVAL)
                return Compiler.analyze(context, RT.list(RT.list(Compiler.FNONCE, PersistentVector.EMPTY, form)));
            return new ThrowExpr(Compiler.analyze(C.EXPRESSION, RT.second(form)));
        }
    }
}
