package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class TryExpr implements Expr {
    public final Expr             tryExpr;
    public final Expr             finallyExpr;
    public final PersistentVector catchExprs;
    public final int              retLocal;
    public final int              finallyLocal;

    public static class CatchClause {
        //final String className;
        public final Class        c;
        public final LocalBinding lb;
        public final Expr         handler;
        Label label;
        Label endLabel;


        public CatchClause(Class c, LocalBinding lb, Expr handler) {
            this.c = c;
            this.lb = lb;
            this.handler = handler;
        }
    }

    public TryExpr(Expr tryExpr, PersistentVector catchExprs, Expr finallyExpr, int retLocal, int finallyLocal) {
        this.tryExpr = tryExpr;
        this.catchExprs = catchExprs;
        this.finallyExpr = finallyExpr;
        this.retLocal = retLocal;
        this.finallyLocal = finallyLocal;
    }

    public Object eval() {
        throw new UnsupportedOperationException("Can't eval try");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        Label startTry = gen.newLabel();
        Label endTry = gen.newLabel();
        Label end = gen.newLabel();
        Label ret = gen.newLabel();
        Label finallyLabel = gen.newLabel();
        for (int i = 0; i < catchExprs.count(); i++) {
            CatchClause clause = (CatchClause) catchExprs.nth(i);
            clause.label = gen.newLabel();
            clause.endLabel = gen.newLabel();
        }

        gen.mark(startTry);
        tryExpr.emit(context, objx, gen);
        if (context != C.STATEMENT)
            gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ISTORE), retLocal);
        gen.mark(endTry);
        if (finallyExpr != null)
            finallyExpr.emit(C.STATEMENT, objx, gen);
        gen.goTo(ret);

        for (int i = 0; i < catchExprs.count(); i++) {
            CatchClause clause = (CatchClause) catchExprs.nth(i);
            gen.mark(clause.label);
            //exception should be on stack
            //put in clause local
            gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ISTORE), clause.lb.idx);
            clause.handler.emit(context, objx, gen);
            if (context != C.STATEMENT)
                gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ISTORE), retLocal);
            gen.mark(clause.endLabel);

            if (finallyExpr != null)
                finallyExpr.emit(C.STATEMENT, objx, gen);
            gen.goTo(ret);
        }
        if (finallyExpr != null) {
            gen.mark(finallyLabel);
            //exception should be on stack
            gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ISTORE), finallyLocal);
            finallyExpr.emit(C.STATEMENT, objx, gen);
            gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ILOAD), finallyLocal);
            gen.throwException();
        }
        gen.mark(ret);
        if (context != C.STATEMENT)
            gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ILOAD), retLocal);
        gen.mark(end);
        for (int i = 0; i < catchExprs.count(); i++) {
            CatchClause clause = (CatchClause) catchExprs.nth(i);
            gen.visitTryCatchBlock(startTry, endTry, clause.label, clause.c.getName().replace('.', '/'));
        }
        if (finallyExpr != null) {
            gen.visitTryCatchBlock(startTry, endTry, finallyLabel, null);
            for (int i = 0; i < catchExprs.count(); i++) {
                CatchClause clause = (CatchClause) catchExprs.nth(i);
                gen.visitTryCatchBlock(clause.label, clause.endLabel, finallyLabel, null);
            }
        }
        for (int i = 0; i < catchExprs.count(); i++) {
            CatchClause clause = (CatchClause) catchExprs.nth(i);
            gen.visitLocalVariable(clause.lb.name, "Ljava/lang/Object;", null, clause.label, clause.endLabel,
                                   clause.lb.idx);
        }
    }

    public boolean hasJavaClass() {
        return tryExpr.hasJavaClass();
    }

    public Class getJavaClass() {
        return tryExpr.getJavaClass();
    }

    public static class Parser implements IParser {

        public Expr parse(C context, Object frm) {
            ISeq form = (ISeq) frm;
//			if(context == C.EVAL || context == C.EXPRESSION)
            if (context != C.RETURN)
                return Compiler.analyze(context, RT.list(RT.list(Compiler.FNONCE, PersistentVector.EMPTY, form)));

            //(try try-expr* catch-expr* finally-expr?)
            //catch-expr: (catch class sym expr*)
            //finally-expr: (finally expr*)

            PersistentVector body = PersistentVector.EMPTY;
            PersistentVector catches = PersistentVector.EMPTY;
            Expr bodyExpr = null;
            Expr finallyExpr = null;
            boolean caught = false;

            int retLocal = Compiler.getAndIncLocalNum();
            int finallyLocal = Compiler.getAndIncLocalNum();
            for (ISeq fs = form.next(); fs != null; fs = fs.next()) {
                Object f = fs.first();
                Object op = (f instanceof ISeq) ? ((ISeq) f).first() : null;
                if (!Util.equals(op, Compiler.CATCH) && !Util.equals(op, Compiler.FINALLY)) {
                    if (caught)
                        throw Util.runtimeException(
                                "Only catch or finally clause can follow catch in try expression");
                    body = body.cons(f);
                } else {
                    if (bodyExpr == null)
                        try {
                            Var.pushThreadBindings(RT.map(Compiler.NO_RECUR, true));
                            bodyExpr = (new BodyExpr.Parser()).parse(context, RT.seq(body));
                        } finally {
                            Var.popThreadBindings();
                        }
                    if (Util.equals(op, Compiler.CATCH)) {
                        Class c = HostExpr.maybeClass(RT.second(f), false);
                        if (c == null)
                            throw new IllegalArgumentException("Unable to resolve classname: " + RT.second(f));
                        if (!(RT.third(f) instanceof Symbol))
                            throw new IllegalArgumentException(
                                    "Bad binding form, expected symbol, got: " + RT.third(f));
                        Symbol sym = (Symbol) RT.third(f);
                        if (sym.getNamespace() != null)
                            throw Util.runtimeException("Can't bind qualified name:" + sym);

                        IPersistentMap dynamicBindings = RT.map(Compiler.LOCAL_ENV, Compiler.LOCAL_ENV.deref(),
                                                                Compiler.NEXT_LOCAL_NUM, Compiler.NEXT_LOCAL_NUM.deref(),
                                                                Compiler.IN_CATCH_FINALLY, RT.T);
                        try {
                            Var.pushThreadBindings(dynamicBindings);
                            LocalBinding lb = Compiler.registerLocal(sym,
                                                                     (Symbol) (RT.second(f) instanceof Symbol ?
                                                                             RT.second(f)
                                                                             : null),
                                                                     null, false);
                            Expr handler = (new BodyExpr.Parser()).parse(C.EXPRESSION, RT.next(RT.next(RT.next(
                                    f))));
                            catches = catches.cons(new CatchClause(c, lb, handler));
                        } finally {
                            Var.popThreadBindings();
                        }
                        caught = true;
                    } else //finally
                    {
                        if (fs.next() != null)
                            throw Util.runtimeException("finally clause must be last in try expression");
                        try {
                            Var.pushThreadBindings(RT.map(Compiler.IN_CATCH_FINALLY, RT.T));
                            finallyExpr = (new BodyExpr.Parser()).parse(C.STATEMENT, RT.next(f));
                        } finally {
                            Var.popThreadBindings();
                        }
                    }
                }
            }
            if (bodyExpr == null) {
                try {
                    Var.pushThreadBindings(RT.map(Compiler.NO_RECUR, true));
                    bodyExpr = (new BodyExpr.Parser()).parse(C.EXPRESSION, RT.seq(body));
                } finally {
                    Var.popThreadBindings();
                }
            }

            return new TryExpr(bodyExpr, catches, finallyExpr, retLocal,
                               finallyLocal);
        }
    }
}
