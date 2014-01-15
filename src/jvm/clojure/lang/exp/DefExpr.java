package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import clojure.lang.Compiler.CompilerException;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/ /*TODO
 * 1. (def #^{:k v} sym "doc" var)
 * 2. （def d (fn[] 1))*/
public class DefExpr implements Expr {
    public final Var     var;
    public final Expr    init;
    public final Expr    meta;
    public final boolean initProvided;
    public final boolean isDynamic;
    public final String  source;
    public final int     line;
    public final int     column;
    final static Method bindRootMethod   = Method.getMethod("void bindRoot(Object)");
    final static Method setTagMethod     = Method.getMethod("void setTag(clojure.lang.Symbol)");
    final static Method setMetaMethod    = Method.getMethod("void setMeta(clojure.lang.IPersistentMap)");
    final static Method setDynamicMethod = Method.getMethod("clojure.lang.Var setDynamic(boolean)");
    final static Method symintern        = Method.getMethod("clojure.lang.Symbol intern(String, String)");

    public DefExpr(String source,
                   int line,
                   int column,
                   Var var,
                   Expr init, //the value of def
                   Expr meta,
                   boolean initProvided,
                   boolean isDynamic) {
        this.source = source;
        this.line = line;
        this.column = column;
        this.var = var;
        this.init = init;
        this.meta = meta;
        this.isDynamic = isDynamic;
        this.initProvided = initProvided;
    }

    private boolean includesExplicitMetadata(MapExpr expr) {
        for (int i = 0; i < expr.keyvals.count(); i += 2) {
            Keyword k = ((KeywordExpr) expr.keyvals.nth(i)).k;
            if ((k != RT.FILE_KEY) &&
                    (k != RT.DECLARED_KEY) &&
                    (k != RT.LINE_KEY) &&
                    (k != RT.COLUMN_KEY))
                return true;
        }
        return false;
    }

    public Object eval() {
        try {
            if (initProvided) {
                var.bindRoot(init.eval());

                if (meta != null) {
                    var.setMeta((IPersistentMap)meta.eval());
                }
            }

            return var.setDynamic(isDynamic);
        } catch (Throwable e) {
            if (!(e instanceof CompilerException))
                throw new CompilerException(source, line, column, e);
            else
                throw (CompilerException) e;
        }
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        objx.emitVar(gen, var);

        //set dynamic
        if (isDynamic) {
            gen.push(isDynamic);
            gen.invokeVirtual(Compiler.VAR_TYPE, setDynamicMethod);
        }

        //set meta
        // what is initProvided : whether there is a init value
        if (meta != null) {
            if (initProvided)//includesExplicitMetadata((MapExpr) meta))
            {
                gen.dup();
                meta.emit(C.EXPRESSION, objx, gen);
                gen.checkCast(Compiler.IPERSISTENTMAP_TYPE);
                gen.invokeVirtual(Compiler.VAR_TYPE, setMetaMethod);
            }
        }
        if (initProvided) {
            gen.dup();
            if (init instanceof FnExpr) {
                ((FnExpr) init).emitForDefn(objx, gen);
            } else
                init.emit(C.EXPRESSION, objx, gen);
            gen.invokeVirtual(Compiler.VAR_TYPE, bindRootMethod);
        }

        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return Var.class;
    }

    public static class Parser implements IParser {

        /*the meta #^{} is not in the form*/
        public Expr parse(C context, Object form) {
            //(def x) or (def x initexpr) or (def x "docstring" initexpr)
            String docstring = null;
            if (RT.count(form) == 4 && (RT.third(form) instanceof String)) {
                docstring = (String) RT.third(form);
                form = RT.list(RT.first(form), RT.second(form), RT.fourth(form));
            }
            if (RT.count(form) > 3)
                throw Util.runtimeException("More than 3 arguments for def > " + form.toString());
            else if (RT.count(form) < 2)
                throw Util.runtimeException("Less than 2 argument for def > "+form.toString());
            else if (!(RT.second(form) instanceof Symbol))
                throw Util.runtimeException("First argument to def must be a Symbol,but is "+form.toString());
            Symbol sym = (Symbol) RT.second(form);
            Var v = Compiler.lookupVar(sym, true);
            if (v == null)
                throw Util.runtimeException("Can't refer to qualified var that doesn't exist > "+sym.toString());
            if (!v.ns.equals(Compiler.currentNS())) {
                if (sym.ns == null)
                    v = Compiler.currentNS().intern(sym);
                else
                    throw Util.runtimeException("Can't create defs outside of current ns");
            }
            IPersistentMap sm = sym.meta();
            boolean isDynamic = RT.booleanCast(RT.get(sm, Compiler.dynamicKey));
            if (isDynamic)
                v.setDynamic();
            if (!isDynamic && sym.name.startsWith("*") && sym.name.endsWith("*") && sym.name.length() > 2) {
                RT.errPrintWriter().format(
                        "Warning: %1$s not declared dynamic and thus is not dynamically rebindable, " +
                                "but its name suggests otherwise. Please either indicate ^:dynamic %1$s or change" +
                                " the name. (%2$s:%3$d)\n",
                        sym, Compiler.SOURCE_PATH.get(), Compiler.LINE.get());
            }

            //put arglists from sym to var
            if (RT.booleanCast(RT.get(sm, Compiler.arglistsKey))) {
                IPersistentMap vm = v.meta();
                //vm = (IPersistentMap) RT.assoc(vm,staticKey,RT.T);
                //drop quote
                vm = (IPersistentMap) RT.assoc(vm, Compiler.arglistsKey, RT.second(sm.valAt(Compiler.arglistsKey)));
                v.setMeta(vm);
            }
            Object source_path = Compiler.SOURCE_PATH.get();
            source_path = source_path == null ? "NO_SOURCE_FILE" : source_path;
            sm = (IPersistentMap) RT.assoc(sm, RT.LINE_KEY, Compiler.LINE.get()).assoc(RT.COLUMN_KEY, Compiler.COLUMN.get()).assoc(
                    RT.FILE_KEY, source_path);
            if (docstring != null)
                sm = (IPersistentMap) RT.assoc(sm, RT.DOC_KEY, docstring);
//			mm = mm.without(RT.DOC_KEY)
//					.without(Keyword.intern(null, "arglists"))
//					.without(RT.FILE_KEY)
//					.without(RT.LINE_KEY)
//					.without(RT.COLUMN_KEY)
//					.without(Keyword.intern(null, "ns"))
//					.without(Keyword.intern(null, "name"))
//					.without(Keyword.intern(null, "added"))
//					.without(Keyword.intern(null, "static"));
            sm = (IPersistentMap) Compiler.elideMeta(sm);
            Expr meta = sm.count() == 0 ? null : Compiler.analyze(context == C.EVAL ? C.EVAL : C.EXPRESSION, sm);
            return new DefExpr((String) Compiler.SOURCE.deref(), Compiler.lineDeref(), Compiler.columnDeref(),
                               v, Compiler.analyze(context == C.EVAL ? context : C.EXPRESSION, RT.third(form),
                                                   v.sym.name),
                               meta, RT.count(form) == 3, isDynamic);
        }
    }
}
