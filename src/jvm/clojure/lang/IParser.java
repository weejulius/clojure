package clojure.lang;

import clojure.lang.Compiler.C;

/**
* Created by jyu on 14-1-15.
*/ /* TODO */
public interface IParser {
    Expr parse(C context, Object form);
}
