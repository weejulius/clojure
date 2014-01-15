package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import clojure.lang.Compiler.PATHTYPE;
import clojure.lang.Compiler.PathNode;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
* Created by jyu on 14-1-15.
*/
public class CaseExpr implements Expr, MaybePrimitiveExpr {
    public final LocalBindingExpr expr;
    public final int              shift, mask, low, high;
    public final Expr                     defaultExpr;
    public final SortedMap<Integer, Expr> tests;
    public final HashMap<Integer, Expr>   thens;
    public final Keyword                  switchType;
    public final Keyword                  testType;
    public final Set<Integer>             skipCheck;
    public final Class                    returnType;
    public final int                      line;
    public final int                      column;

    final static Type   NUMBER_TYPE    = Type.getType(Number.class);
    final static Method intValueMethod = Method.getMethod("int intValue()");

    final static Method  hashMethod      = Method.getMethod("int hash(Object)");
    final static Method  hashCodeMethod  = Method.getMethod("int hashCode()");
    final static Method  equivMethod     = Method.getMethod("boolean equiv(Object, Object)");
    final static Keyword compactKey      = Keyword.intern(null, "compact");
    final static Keyword sparseKey       = Keyword.intern(null, "sparse");
    final static Keyword hashIdentityKey = Keyword.intern(null, "hash-identity");
    final static Keyword hashEquivKey    = Keyword.intern(null, "hash-equiv");
    final static Keyword intKey          = Keyword.intern(null, "int");

    //(case* expr shift mask default map<minhash, [test then]> table-type test-type skip-check?)
    public CaseExpr(int line,
                    int column,
                    LocalBindingExpr expr,
                    int shift,
                    int mask,
                    int low,
                    int high,
                    Expr defaultExpr,
                    SortedMap<Integer, Expr> tests,
                    HashMap<Integer, Expr> thens,
                    Keyword switchType,
                    Keyword testType,
                    Set<Integer> skipCheck) {
        this.expr = expr;
        this.shift = shift;
        this.mask = mask;
        this.low = low;
        this.high = high;
        this.defaultExpr = defaultExpr;
        this.tests = tests;
        this.thens = thens;
        this.line = line;
        this.column = column;
        if (switchType != compactKey && switchType != sparseKey)
            throw new IllegalArgumentException("Unexpected switch type: " + switchType);
        this.switchType = switchType;
        if (testType != intKey && testType != hashEquivKey && testType != hashIdentityKey)
            throw new IllegalArgumentException("Unexpected test type: " + switchType);
        this.testType = testType;
        this.skipCheck = skipCheck;
        Collection<Expr> returns = new ArrayList(thens.values());
        returns.add(defaultExpr);
        this.returnType = Compiler.maybeJavaClass(returns);
        if (RT.count(skipCheck) > 0 && RT.booleanCast(RT.WARN_ON_REFLECTION.deref())) {
            RT.errPrintWriter()
                    .format("Performance warning, %s:%d:%d - hash collision of some case test constants; if " +
                                    "selected, those entries will be tested sequentially.\n",
                            Compiler.SOURCE_PATH.deref(), line, column);
        }
    }

    public boolean hasJavaClass() {
        return returnType != null;
    }

    public boolean canEmitPrimitive() {
        return Util.isPrimitive(returnType);
    }

    public Class getJavaClass() {
        return returnType;
    }

    public Object eval() {
        throw new UnsupportedOperationException("Can't eval case");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        doEmit(context, objx, gen, false);
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        doEmit(context, objx, gen, true);
    }

    public void doEmit(C context, ObjExpr objx, GeneratorAdapter gen, boolean emitUnboxed) {
        Label defaultLabel = gen.newLabel();
        Label endLabel = gen.newLabel();
        SortedMap<Integer, Label> labels = new TreeMap();

        for (Integer i : tests.keySet()) {
            labels.put(i, gen.newLabel());
        }

        gen.visitLineNumber(line, gen.mark());

        Class primExprClass = Compiler.maybePrimitiveType(expr);
        Type primExprType = primExprClass == null ? null : Type.getType(primExprClass);

        if (testType == intKey)
            emitExprForInts(objx, gen, primExprType, defaultLabel);
        else
            emitExprForHashes(objx, gen);

        if (switchType == sparseKey) {
            Label[] la = new Label[labels.size()];
            la = labels.values().toArray(la);
            int[] ints = Numbers.int_array(tests.keySet());
            gen.visitLookupSwitchInsn(defaultLabel, ints, la);
        } else {
            Label[] la = new Label[(high - low) + 1];
            for (int i = low; i <= high; i++) {
                la[i - low] = labels.containsKey(i) ? labels.get(i) : defaultLabel;
            }
            gen.visitTableSwitchInsn(low, high, defaultLabel, la);
        }

        for (Integer i : labels.keySet()) {
            gen.mark(labels.get(i));
            if (testType == intKey)
                emitThenForInts(objx, gen, primExprType, tests.get(i), thens.get(i), defaultLabel, emitUnboxed);
            else if (RT.contains(skipCheck, i) == RT.T)
                emitExpr(objx, gen, thens.get(i), emitUnboxed);
            else
                emitThenForHashes(objx, gen, tests.get(i), thens.get(i), defaultLabel, emitUnboxed);
            gen.goTo(endLabel);
        }

        gen.mark(defaultLabel);
        emitExpr(objx, gen, defaultExpr, emitUnboxed);
        gen.mark(endLabel);
        if (context == C.STATEMENT)
            gen.pop();
    }

    private boolean isShiftMasked() {
        return mask != 0;
    }

    private void emitShiftMask(GeneratorAdapter gen) {
        if (isShiftMasked()) {
            gen.push(shift);
            gen.visitInsn(Opcodes.ISHR);
            gen.push(mask);
            gen.visitInsn(Opcodes.IAND);
        }
    }

    private void emitExprForInts(ObjExpr objx, GeneratorAdapter gen, Type exprType, Label defaultLabel) {
        if (exprType == null) {
            if (RT.booleanCast(RT.WARN_ON_REFLECTION.deref())) {
                RT.errPrintWriter()
                        .format("Performance warning, %s:%d:%d - case has int tests, but tested expression is not " +
                                        "primitive.\n",
                                Compiler.SOURCE_PATH.deref(), line, column);
            }
            expr.emit(C.EXPRESSION, objx, gen);
            gen.instanceOf(NUMBER_TYPE);
            gen.ifZCmp(GeneratorAdapter.EQ, defaultLabel);
            expr.emit(C.EXPRESSION, objx, gen);
            gen.checkCast(NUMBER_TYPE);
            gen.invokeVirtual(NUMBER_TYPE, intValueMethod);
            emitShiftMask(gen);
        } else if (exprType == Type.LONG_TYPE
                || exprType == Type.INT_TYPE
                || exprType == Type.SHORT_TYPE
                || exprType == Type.BYTE_TYPE) {
            expr.emitUnboxed(C.EXPRESSION, objx, gen);
            gen.cast(exprType, Type.INT_TYPE);
            emitShiftMask(gen);
        } else {
            gen.goTo(defaultLabel);
        }
    }

    private void emitThenForInts(ObjExpr objx,
                                 GeneratorAdapter gen,
                                 Type exprType,
                                 Expr test,
                                 Expr then,
                                 Label defaultLabel,
                                 boolean emitUnboxed) {
        if (exprType == null) {
            expr.emit(C.EXPRESSION, objx, gen);
            test.emit(C.EXPRESSION, objx, gen);
            gen.invokeStatic(Compiler.UTIL_TYPE, equivMethod);
            gen.ifZCmp(GeneratorAdapter.EQ, defaultLabel);
            emitExpr(objx, gen, then, emitUnboxed);
        } else if (exprType == Type.LONG_TYPE) {
            ((NumberExpr) test).emitUnboxed(C.EXPRESSION, objx, gen);
            expr.emitUnboxed(C.EXPRESSION, objx, gen);
            gen.ifCmp(Type.LONG_TYPE, GeneratorAdapter.NE, defaultLabel);
            emitExpr(objx, gen, then, emitUnboxed);
        } else if (exprType == Type.INT_TYPE
                || exprType == Type.SHORT_TYPE
                || exprType == Type.BYTE_TYPE) {
            if (isShiftMasked()) {
                ((NumberExpr) test).emitUnboxed(C.EXPRESSION, objx, gen);
                expr.emitUnboxed(C.EXPRESSION, objx, gen);
                gen.cast(exprType, Type.LONG_TYPE);
                gen.ifCmp(Type.LONG_TYPE, GeneratorAdapter.NE, defaultLabel);
            }
            // else direct match
            emitExpr(objx, gen, then, emitUnboxed);
        } else {
            gen.goTo(defaultLabel);
        }
    }

    private void emitExprForHashes(ObjExpr objx, GeneratorAdapter gen) {
        expr.emit(C.EXPRESSION, objx, gen);
        gen.invokeStatic(Compiler.UTIL_TYPE, hashMethod);
        emitShiftMask(gen);
    }

    private void emitThenForHashes(ObjExpr objx,
                                   GeneratorAdapter gen,
                                   Expr test,
                                   Expr then,
                                   Label defaultLabel,
                                   boolean emitUnboxed) {
        expr.emit(C.EXPRESSION, objx, gen);
        test.emit(C.EXPRESSION, objx, gen);
        if (testType == hashIdentityKey) {
            gen.visitJumpInsn(Opcodes.IF_ACMPNE, defaultLabel);
        } else {
            gen.invokeStatic(Compiler.UTIL_TYPE, equivMethod);
            gen.ifZCmp(GeneratorAdapter.EQ, defaultLabel);
        }
        emitExpr(objx, gen, then, emitUnboxed);
    }

    private static void emitExpr(ObjExpr objx, GeneratorAdapter gen, Expr expr, boolean emitUnboxed) {
        if (emitUnboxed && expr instanceof MaybePrimitiveExpr)
            ((MaybePrimitiveExpr) expr).emitUnboxed(C.EXPRESSION, objx, gen);
        else
            expr.emit(C.EXPRESSION, objx, gen);
    }


    public static class Parser implements IParser {
        //(case* expr shift mask default map<minhash, [test then]> table-type test-type skip-check?)
        //prepared by case macro and presumed correct
        //case macro binds actual expr in let so expr is always a local,
        //no need to worry about multiple evaluation
        public Expr parse(C context, Object frm) {
            ISeq form = (ISeq) frm;
            if (context == C.EVAL)
                return Compiler.analyze(context, RT.list(RT.list(Compiler.FNONCE, PersistentVector.EMPTY, form)));
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
                Expr testExpr = testType == intKey
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
}
