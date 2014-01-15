package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import clojure.lang.Compiler.PATHTYPE;
import clojure.lang.Compiler.PathNode;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class IfExpr implements Expr, MaybePrimitiveExpr {
    public final Expr testExpr;
    public final Expr thenExpr;
    public final Expr elseExpr;
    public final int  line;
    public final int  column;


    public IfExpr(int line, int column, Expr testExpr, Expr thenExpr, Expr elseExpr) {
        this.testExpr = testExpr;
        this.thenExpr = thenExpr;
        this.elseExpr = elseExpr;
        this.line = line;
        this.column = column;
    }

    public Object eval() {
        Object t = testExpr.eval();
        if (t != null && t != Boolean.FALSE)
            return thenExpr.eval();
        return elseExpr.eval();
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        doEmit(context, objx, gen, false);
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        doEmit(context, objx, gen, true);
    }

    public void doEmit(C context, ObjExpr objx, GeneratorAdapter gen, boolean emitUnboxed) {
        Label nullLabel = gen.newLabel();
        Label falseLabel = gen.newLabel();
        Label endLabel = gen.newLabel();

        gen.visitLineNumber(line, gen.mark());

        if (testExpr instanceof StaticMethodExpr && ((StaticMethodExpr) testExpr).canEmitIntrinsicPredicate()) {
            ((StaticMethodExpr) testExpr).emitIntrinsicPredicate(C.EXPRESSION, objx, gen, falseLabel);
        } else if (Compiler.maybePrimitiveType(testExpr) == boolean.class) {
            ((MaybePrimitiveExpr) testExpr).emitUnboxed(C.EXPRESSION, objx, gen);
            gen.ifZCmp(gen.EQ, falseLabel);
        } else {
            testExpr.emit(C.EXPRESSION, objx, gen);
            gen.dup();
            gen.ifNull(nullLabel);
            gen.getStatic(Compiler.BOOLEAN_OBJECT_TYPE, "FALSE", Compiler.BOOLEAN_OBJECT_TYPE);
            gen.visitJumpInsn(Opcodes.IF_ACMPEQ, falseLabel);
        }
        if (emitUnboxed)
            ((MaybePrimitiveExpr) thenExpr).emitUnboxed(context, objx, gen);
        else
            thenExpr.emit(context, objx, gen);
        gen.goTo(endLabel);
        gen.mark(nullLabel);
        gen.pop();
        gen.mark(falseLabel);
        if (emitUnboxed)
            ((MaybePrimitiveExpr) elseExpr).emitUnboxed(context, objx, gen);
        else
            elseExpr.emit(context, objx, gen);
        gen.mark(endLabel);
    }

    public boolean hasJavaClass() {
        return thenExpr.hasJavaClass()
                && elseExpr.hasJavaClass()
                &&
                (thenExpr.getJavaClass() == elseExpr.getJavaClass()
                        || thenExpr.getJavaClass() == Compiler.RECUR_CLASS
                        || elseExpr.getJavaClass() == Compiler.RECUR_CLASS
                        || (thenExpr.getJavaClass() == null && !elseExpr.getJavaClass().isPrimitive())
                        || (elseExpr.getJavaClass() == null && !thenExpr.getJavaClass().isPrimitive()));
    }

    public boolean canEmitPrimitive() {
        try {
            return thenExpr instanceof MaybePrimitiveExpr
                    && elseExpr instanceof MaybePrimitiveExpr
                    && (thenExpr.getJavaClass() == elseExpr.getJavaClass()
                    || thenExpr.getJavaClass() == Compiler.RECUR_CLASS
                    || elseExpr.getJavaClass() == Compiler.RECUR_CLASS)
                    && ((MaybePrimitiveExpr) thenExpr).canEmitPrimitive()
                    && ((MaybePrimitiveExpr) elseExpr).canEmitPrimitive();
        } catch (Exception e) {
            return false;
        }
    }

    public Class getJavaClass() {
        Class thenClass = thenExpr.getJavaClass();
        if (thenClass != null && thenClass != Compiler.RECUR_CLASS)
            return thenClass;
        return elseExpr.getJavaClass();
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object frm) {
            ISeq form = (ISeq) frm;
            //(if test then) or (if test then else)
            if (form.count() > 4)
                throw Util.runtimeException("Too many arguments to if");
            else if (form.count() < 3)
                throw Util.runtimeException("Too few arguments to if");
            PathNode branch = new PathNode(PATHTYPE.BRANCH, (PathNode) Compiler.CLEAR_PATH.get());
            Expr testexpr = Compiler.analyze(context == C.EVAL ? context : C.EXPRESSION, RT.second(form));
            Expr thenexpr, elseexpr;
            try {
                Var.pushThreadBindings(
                        RT.map(Compiler.CLEAR_PATH, new PathNode(PATHTYPE.PATH, branch)));
                thenexpr = Compiler.analyze(context, RT.third(form));
            } finally {
                Var.popThreadBindings();
            }
            try {
                Var.pushThreadBindings(
                        RT.map(Compiler.CLEAR_PATH, new PathNode(PATHTYPE.PATH, branch)));
                elseexpr = Compiler.analyze(context, RT.fourth(form));
            } finally {
                Var.popThreadBindings();
            }
            return new IfExpr(Compiler.lineDeref(),
                              Compiler.columnDeref(),
                              testexpr,
                              thenexpr,
                              elseexpr);
        }
    }
}
