package clojure.lang.exp;

import clojure.lang.Expr;

/**
* Created by jyu on 14-1-15.
*/ /* what is Untyped expression TODO*/
public abstract class UntypedExpr implements Expr {

    public Class getJavaClass() {
        throw new IllegalArgumentException("Has no Java class");
    }

    public boolean hasJavaClass() {
        return false;
    }
}
