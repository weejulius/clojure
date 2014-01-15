package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/
public class ListExpr implements Expr {
    public final IPersistentVector args;
    final static Method arrayToListMethod = Method.getMethod("clojure.lang.ISeq arrayToList(Object[])");


    public ListExpr(IPersistentVector args) {
        this.args = args;
    }

    public Object eval() {
        IPersistentVector ret = PersistentVector.EMPTY;
        for (int i = 0; i < args.count(); i++)
            ret = (IPersistentVector) ret.cons(((Expr) args.nth(i)).eval());
        return ret.seq();
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        MethodExpr.emitArgsAsArray(args, objx, gen);
        gen.invokeStatic(clojure.lang.Compiler.RT_TYPE, arrayToListMethod);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return IPersistentList.class;
    }

}
