package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class BodyExpr implements Expr, MaybePrimitiveExpr {
    PersistentVector exprs;

    public final PersistentVector exprs() {
        return exprs;
    }

    public BodyExpr(PersistentVector exprs) {
        this.exprs = exprs;
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object frms) {
            ISeq forms = (ISeq) frms;
            if (Util.equals(RT.first(forms), Compiler.DO))
                forms = RT.next(forms);
            PersistentVector exprs = PersistentVector.EMPTY;
            for (; forms != null; forms = forms.next()) {
                Expr e = (context != C.EVAL &&
                        (context == C.STATEMENT || forms.next() != null)) ?
                        Compiler.analyze(C.STATEMENT, forms.first())
                        :
                        Compiler.analyze(context, forms.first());
                exprs = exprs.cons(e);
            }
            if (exprs.count() == 0)
                exprs = exprs.cons(Compiler.NIL_EXPR);
            return new BodyExpr(exprs);
        }
    }

    public Object eval() {
        Object ret = null;
        for (Object o : exprs) {
            Expr e = (Expr) o;
            ret = e.eval();
        }
        return ret;
    }

    public boolean canEmitPrimitive() {
        return lastExpr() instanceof MaybePrimitiveExpr && ((MaybePrimitiveExpr) lastExpr()).canEmitPrimitive();
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        for (int i = 0; i < exprs.count() - 1; i++) {
            Expr e = (Expr) exprs.nth(i);
            e.emit(C.STATEMENT, objx, gen);
        }
        MaybePrimitiveExpr last = (MaybePrimitiveExpr) exprs.nth(exprs.count() - 1);
        last.emitUnboxed(context, objx, gen);
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        for (int i = 0; i < exprs.count() - 1; i++) {
            Expr e = (Expr) exprs.nth(i);
            e.emit(C.STATEMENT, objx, gen);
        }
        Expr last = (Expr) exprs.nth(exprs.count() - 1);
        last.emit(context, objx, gen);
    }

    public boolean hasJavaClass() {
        return lastExpr().hasJavaClass();
    }

    public Class getJavaClass() {
        return lastExpr().getJavaClass();
    }

    private Expr lastExpr() {
        return (Expr) exprs.nth(exprs.count() - 1);
    }
}
