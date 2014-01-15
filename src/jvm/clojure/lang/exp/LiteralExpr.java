package clojure.lang.exp;

import clojure.lang.Expr;

/**
* Created by jyu on 14-1-15.
*/
public abstract class LiteralExpr implements Expr {
    abstract Object val();

    public Object eval() {
        return val();
    }
}
