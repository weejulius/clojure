package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import clojure.lang.Compiler.PATHTYPE;
import clojure.lang.Compiler.PathNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
* Created by jyu on 14-1-15.
*/
public class Parser implements IParser {
    //(case* expr shift mask default map<minhash, [test then]> table-type test-type skip-check?)
    //prepared by case macro and presumed correct
    //case macro binds actual expr in let so expr is always a local,
    //no need to worry about multiple evaluation
    public Expr parse(C context, Object frm) {
        ISeq form = (ISeq) frm;
        if (context == C.EVAL)
            return clojure.lang.Compiler.analyze(context, RT.list(RT.list(Compiler.FNONCE, PersistentVector.EMPTY,
                                                                          form)));
        PersistentVector args = PersistentVector.create(form.next());

        Object exprForm = args.nth(0);
        int shift = ((Number) args.nth(1)).intValue();
        int mask = ((Number) args.nth(2)).intValue();
        Object defaultForm = args.nth(3);
        Map caseMap = (Map) args.nth(4);
        Keyword switchType = ((Keyword) args.nth(5));
        Keyword testType = ((Keyword) args.nth(6));
        Set skipCheck = RT.count(args) < 8 ? null : (Set) args.nth(7);

        ISeq keys = RT.keys(caseMap);
        int low = ((Number) RT.first(keys)).intValue();
        int high = ((Number) RT.nth(keys, RT.count(keys) - 1)).intValue();

        LocalBindingExpr testexpr = (LocalBindingExpr) Compiler.analyze(C.EXPRESSION, exprForm);
        testexpr.shouldClear = false;

        SortedMap<Integer, Expr> tests = new TreeMap();
        HashMap<Integer, Expr> thens = new HashMap();

        PathNode branch = new PathNode(PATHTYPE.BRANCH, (PathNode) Compiler.CLEAR_PATH.get());

        for (Object o : caseMap.entrySet()) {
            Map.Entry e = (Map.Entry) o;
            Integer minhash = ((Number) e.getKey()).intValue();
            Object pair = e.getValue(); // [test-val then-expr]
            Expr testExpr = testType == CaseExpr.intKey
                    ? NumberExpr.parse(((Number) RT.first(pair)).intValue())
                    : new ConstantExpr(RT.first(pair));
            tests.put(minhash, testExpr);

            Expr thenExpr;
            try {
                Var.pushThreadBindings(
                        RT.map(Compiler.CLEAR_PATH, new PathNode(PATHTYPE.PATH, branch)));
                thenExpr = Compiler.analyze(context, RT.second(pair));
            } finally {
                Var.popThreadBindings();
            }
            thens.put(minhash, thenExpr);
        }

        Expr defaultExpr;
        try {
            Var.pushThreadBindings(
                    RT.map(Compiler.CLEAR_PATH, new PathNode(PATHTYPE.PATH, branch)));
            defaultExpr = Compiler.analyze(context, args.nth(3));
        } finally {
            Var.popThreadBindings();
        }

        int line = ((Number) Compiler.LINE.deref()).intValue();
        int column = ((Number) Compiler.COLUMN.deref()).intValue();
        return new CaseExpr(line, column, testexpr, shift, mask, low, high,
                            defaultExpr, tests, thens, switchType, testType, skipCheck);
    }
}
