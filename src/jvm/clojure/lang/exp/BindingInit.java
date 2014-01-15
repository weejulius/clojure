package clojure.lang.exp;

import clojure.lang.Expr;

/**
* Created by jyu on 14-1-15.
*/
public class BindingInit {
    LocalBinding binding;
    Expr init;

    public final LocalBinding binding() {
        return binding;
    }

    public final Expr init() {
        return init;
    }

    public BindingInit(LocalBinding binding, Expr init) {
        this.binding = binding;
        this.init = init;
    }
}
