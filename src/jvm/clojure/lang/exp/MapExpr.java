package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/
public class MapExpr implements Expr {
    public final IPersistentVector keyvals;
    final static Method mapMethod           = Method.getMethod("clojure.lang.IPersistentMap map(Object[])");
    final static Method mapUniqueKeysMethod = Method.getMethod(
            "clojure.lang.IPersistentMap mapUniqueKeys(Object[])");


    public MapExpr(IPersistentVector keyvals) {
        this.keyvals = keyvals;
    }

    public Object eval() {
        Object[] ret = new Object[keyvals.count()];
        for (int i = 0; i < keyvals.count(); i++)
            ret[i] = ((Expr) keyvals.nth(i)).eval();
        return RT.map(ret);
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        boolean allKeysConstant = true;
        boolean allConstantKeysUnique = true;
        IPersistentSet constantKeys = PersistentHashSet.EMPTY;
        for (int i = 0; i < keyvals.count(); i += 2) {
            Expr k = (Expr) keyvals.nth(i);
            if (k instanceof LiteralExpr) {
                Object kval = k.eval();
                if (constantKeys.contains(kval))
                    allConstantKeysUnique = false;
                else
                    constantKeys = (IPersistentSet) constantKeys.cons(kval);
            } else
                allKeysConstant = false;
        }
        MethodExpr.emitArgsAsArray(keyvals, objx, gen);
        if ((allKeysConstant && allConstantKeysUnique) || (keyvals.count() <= 2))
            gen.invokeStatic(Compiler.RT_TYPE, mapUniqueKeysMethod);
        else
            gen.invokeStatic(Compiler.RT_TYPE, mapMethod);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return IPersistentMap.class;
    }


    static public Expr parse(C context, IPersistentMap form) {
        IPersistentVector keyvals = PersistentVector.EMPTY;
        boolean keysConstant = true;
        boolean valsConstant = true;
        boolean allConstantKeysUnique = true;
        IPersistentSet constantKeys = PersistentHashSet.EMPTY;
        for (ISeq s = RT.seq(form); s != null; s = s.next()) {
            IMapEntry e = (IMapEntry) s.first();
            Expr k = Compiler.analyze(context == C.EVAL ? context : C.EXPRESSION, e.key());
            Expr v = Compiler.analyze(context == C.EVAL ? context : C.EXPRESSION, e.val());
            keyvals = (IPersistentVector) keyvals.cons(k);
            keyvals = (IPersistentVector) keyvals.cons(v);
            if (k instanceof LiteralExpr) {
                Object kval = k.eval();
                if (constantKeys.contains(kval))
                    allConstantKeysUnique = false;
                else
                    constantKeys = (IPersistentSet) constantKeys.cons(kval);
            } else
                keysConstant = false;
            if (!(v instanceof LiteralExpr))
                valsConstant = false;
        }

        Expr ret = new MapExpr(keyvals);
        if (form instanceof IObj && ((IObj) form).meta() != null)
            return new MetaExpr(ret, MapExpr
                    .parse(context == C.EVAL ? context : C.EXPRESSION, ((IObj) form).meta()));
        else if (keysConstant) {
            // TBD: Add more detail to exception thrown below.
            if (!allConstantKeysUnique)
                throw new IllegalArgumentException("Duplicate constant keys in map");
            if (valsConstant) {
                IPersistentMap m = PersistentHashMap.EMPTY;
                for (int i = 0; i < keyvals.length(); i += 2) {
                    m = m.assoc(((LiteralExpr) keyvals.nth(i)).val(), ((LiteralExpr) keyvals.nth(i + 1)).val());
                }
//				System.err.println("Constant: " + m)a;
                return new ConstantExpr(m);
            } else
                return ret;
        } else
            return ret;
    }
}
