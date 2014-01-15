package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/
public class SetExpr implements Expr {
    public final IPersistentVector keys;
    final static Method setMethod = Method.getMethod("clojure.lang.IPersistentSet set(Object[])");


    public SetExpr(IPersistentVector keys) {
        this.keys = keys;
    }

    public Object eval() {
        Object[] ret = new Object[keys.count()];
        for (int i = 0; i < keys.count(); i++)
            ret[i] = ((Expr) keys.nth(i)).eval();
        return RT.set(ret);
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        MethodExpr.emitArgsAsArray(keys, objx, gen);
        gen.invokeStatic(Compiler.RT_TYPE, setMethod);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return IPersistentSet.class;
    }


    static public Expr parse(C context, IPersistentSet form) {
        IPersistentVector keys = PersistentVector.EMPTY;
        boolean constant = true;

        for (ISeq s = RT.seq(form); s != null; s = s.next()) {
            Object e = s.first();
            Expr expr = Compiler.analyze(context == C.EVAL ? context : C.EXPRESSION, e);
            keys = (IPersistentVector) keys.cons(expr);
            if (!(expr instanceof LiteralExpr))
                constant = false;
        }
        Expr ret = new SetExpr(keys);
        if (form instanceof IObj && ((IObj) form).meta() != null)
            return new MetaExpr(ret, MapExpr
                    .parse(context == C.EVAL ? context : C.EXPRESSION, ((IObj) form).meta()));
        else if (constant) {
            IPersistentSet set = PersistentHashSet.EMPTY;
            for (int i = 0; i < keys.count(); i++) {
                LiteralExpr ve = (LiteralExpr) keys.nth(i);
                set = (IPersistentSet) set.cons(ve.val());
            }
//			System.err.println("Constant: " + set);
            return new ConstantExpr(set);
        } else
            return ret;
    }
}
