package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/
public class VectorExpr implements Expr {
    public final IPersistentVector args;
    final static Method vectorMethod = Method.getMethod("clojure.lang.IPersistentVector vector(Object[])");


    public VectorExpr(IPersistentVector args) {
        this.args = args;
    }

    public Object eval() {
        IPersistentVector ret = PersistentVector.EMPTY;
        for (int i = 0; i < args.count(); i++)
            ret = (IPersistentVector) ret.cons(((Expr) args.nth(i)).eval());
        return ret;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        MethodExpr.emitArgsAsArray(args, objx, gen);
        gen.invokeStatic(Compiler.RT_TYPE, vectorMethod);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return IPersistentVector.class;
    }

    static public Expr parse(C context, IPersistentVector form) {
        boolean constant = true;

        IPersistentVector args = PersistentVector.EMPTY;
        for (int i = 0; i < form.count(); i++) {
            Expr v = Compiler.analyze(context == C.EVAL ? context : C.EXPRESSION, form.nth(i));
            args = (IPersistentVector) args.cons(v);
            if (!(v instanceof LiteralExpr))
                constant = false;
        }
        Expr ret = new VectorExpr(args);
        if (form instanceof IObj && ((IObj) form).meta() != null)
            return new MetaExpr(ret, MapExpr
                    .parse(context == C.EVAL ? context : C.EXPRESSION, ((IObj) form).meta()));
        else if (constant) {
            PersistentVector rv = PersistentVector.EMPTY;
            for (int i = 0; i < args.count(); i++) {
                LiteralExpr ve = (LiteralExpr) args.nth(i);
                rv = rv.cons(ve.val());
            }
//			System.err.println("Constant: " + rv);
            return new ConstantExpr(rv);
        } else
            return ret;
    }

}
