package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/**
* Created by jyu on 14-1-15.
*/
public class FnExpr extends ObjExpr {
    final static Type aFnType    = Type.getType(AFunction.class);
    final static Type restFnType = Type.getType(RestFn.class);
    //if there is a variadic overload (there can only be one) it is stored here
    FnMethod variadicMethod = null;
    IPersistentCollection methods;
    private boolean hasPrimSigs;
    private boolean hasMeta;
    //	String superName = null;

    public FnExpr(Object tag) {
        super(tag);
    }

    public boolean hasJavaClass() {
        return true;
    }

    boolean supportsMeta() {
        return hasMeta;
    }

    public Class getJavaClass() {
        return AFunction.class;
    }

    protected void emitMethods(ClassVisitor cv) {
        //override of invoke/doInvoke for each method
        for (ISeq s = RT.seq(methods); s != null; s = s.next()) {
            ObjMethod method = (ObjMethod) s.first();
            method.emit(this, cv);
        }

        if (isVariadic()) {
            GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                                                        Method.getMethod("int getRequiredArity()"),
                                                        null,
                                                        null,
                                                        cv);
            gen.visitCode();
            gen.push(variadicMethod.reqParms.count());
            gen.returnValue();
            gen.endMethod();
        }
    }

    public static Expr parse(C context, ISeq form, String name) {
        ISeq origForm = form;
        FnExpr fn = new FnExpr(Compiler.tagOf(form));
        fn.src = form;
        ObjMethod enclosingMethod = (ObjMethod) Compiler.METHOD.deref();
        if (((IMeta) form.first()).meta() != null) {
            fn.onceOnly = RT.booleanCast(RT.get(RT.meta(form.first()), Keyword.intern(null, "once")));
//			fn.superName = (String) RT.get(RT.meta(form.first()), Keyword.intern(null, "super-name"));
        }
        //fn.thisName = name;
        String basename = enclosingMethod != null ?
                (enclosingMethod.objx.name + "$")
                : //"clojure.fns." +
                (Compiler.munge(Compiler.currentNS().name.name) + "$");
        if (RT.second(form) instanceof Symbol)
            name = ((Symbol) RT.second(form)).name;
        String simpleName = name != null ?
                (Compiler.munge(name).replace(".", "_DOT_")
                        + (enclosingMethod != null ? "__" + RT.nextID() : ""))
                : ("fn"
                + "__" + RT.nextID());
        fn.name = basename + simpleName;
        fn.internalName = fn.name.replace('.', '/');
        fn.objtype = Type.getObjectType(fn.internalName);
        ArrayList<String> prims = new ArrayList();
        try {
            Var.pushThreadBindings(
                    RT.mapUniqueKeys(Compiler.CONSTANTS, PersistentVector.EMPTY,
                                     Compiler.CONSTANT_IDS, new IdentityHashMap(),
                                     Compiler.KEYWORDS, PersistentHashMap.EMPTY,
                                     Compiler.VARS, PersistentHashMap.EMPTY,
                                     Compiler.KEYWORD_CALLSITES, PersistentVector.EMPTY,
                                     Compiler.PROTOCOL_CALLSITES, PersistentVector.EMPTY,
                                     Compiler.VAR_CALLSITES, Compiler.emptyVarCallSites(),
                                     Compiler.NO_RECUR, null
                    ));

            //arglist might be preceded by symbol naming this fn
            if (RT.second(form) instanceof Symbol) {
                Symbol nm = (Symbol) RT.second(form);
                fn.thisName = nm.name;
                fn.isStatic = false; //RT.booleanCast(RT.get(nm.meta(), staticKey));
                form = RT.cons(Compiler.FN, RT.next(RT.next(form)));
            }

            //now (fn [args] body...) or (fn ([args] body...) ([args2] body2...) ...)
            //turn former into latter
            if (RT.second(form) instanceof IPersistentVector)
                form = RT.list(Compiler.FN, RT.next(form));
            fn.line = Compiler.lineDeref();
            fn.column = Compiler.columnDeref();
            FnMethod[] methodArray = new FnMethod[Compiler.MAX_POSITIONAL_ARITY + 1];
            FnMethod variadicMethod = null;
            for (ISeq s = RT.next(form); s != null; s = RT.next(s)) {
                FnMethod f = FnMethod.parse(fn, (ISeq) RT.first(s), fn.isStatic);
                if (f.isVariadic()) {
                    if (variadicMethod == null)
                        variadicMethod = f;
                    else
                        throw Util.runtimeException("Can't have more than 1 variadic overload");
                } else if (methodArray[f.reqParms.count()] == null)
                    methodArray[f.reqParms.count()] = f;
                else
                    throw Util.runtimeException("Can't have 2 overloads with same arity");
                if (f.prim != null)
                    prims.add(f.prim);
            }
            if (variadicMethod != null) {
                for (int i = variadicMethod.reqParms.count() + 1; i <= Compiler.MAX_POSITIONAL_ARITY; i++)
                    if (methodArray[i] != null)
                        throw Util.runtimeException(
                                "Can't have fixed arity function with more params than variadic function");
            }

            if (fn.isStatic && fn.closes.count() > 0)
                throw new IllegalArgumentException("static fns can't be closures");
            IPersistentCollection methods = null;
            for (int i = 0; i < methodArray.length; i++)
                if (methodArray[i] != null)
                    methods = RT.conj(methods, methodArray[i]);
            if (variadicMethod != null)
                methods = RT.conj(methods, variadicMethod);

            fn.methods = methods;
            fn.variadicMethod = variadicMethod;
            fn.keywords = (IPersistentMap) Compiler.KEYWORDS.deref();
            fn.vars = (IPersistentMap) Compiler.VARS.deref();
            fn.constants = (PersistentVector) Compiler.CONSTANTS.deref();
            fn.keywordCallsites = (IPersistentVector) Compiler.KEYWORD_CALLSITES.deref();
            fn.protocolCallsites = (IPersistentVector) Compiler.PROTOCOL_CALLSITES.deref();
            fn.varCallsites = (IPersistentSet) Compiler.VAR_CALLSITES.deref();

            fn.constantsID = RT.nextID();
//			DynamicClassLoader loader = (DynamicClassLoader) LOADER.get();
//			loader.registerConstants(fn.constantsID, fn.constants.toArray());
        } finally {
            Var.popThreadBindings();
        }
        fn.hasPrimSigs = prims.size() > 0;
        IPersistentMap fmeta = RT.meta(origForm);
        if (fmeta != null)
            fmeta = fmeta.without(RT.LINE_KEY).without(RT.COLUMN_KEY).without(RT.FILE_KEY);

        fn.hasMeta = RT.count(fmeta) > 0;

        try {
            fn.compile(fn.isVariadic() ? "clojure/lang/RestFn" : "clojure/lang/AFunction",
                       (prims.size() == 0) ?
                               null
                               : prims.toArray(new String[prims.size()]),
                       fn.onceOnly);
        } catch (IOException e) {
            throw Util.sneakyThrow(e);
        }
        fn.getCompiledClass();

        if (fn.supportsMeta()) {
            //System.err.println(name + " supports meta");
            return new MetaExpr(fn, MapExpr
                    .parse(context == C.EVAL ? context : C.EXPRESSION, fmeta));
        } else
            return fn;
    }

    public final ObjMethod variadicMethod() {
        return variadicMethod;
    }

    boolean isVariadic() {
        return variadicMethod != null;
    }

    public final IPersistentCollection methods() {
        return methods;
    }

    public void emitForDefn(ObjExpr objx, GeneratorAdapter gen) {
//		if(!hasPrimSigs && closes.count() == 0)
//			{
//			Type thunkType = Type.getType(FnLoaderThunk.class);
////			presumes var on stack
//			gen.dup();
//			gen.newInstance(thunkType);
//			gen.dupX1();
//			gen.swap();
//			gen.push(internalName.replace('/','.'));
//			gen.invokeConstructor(thunkType,Method.getMethod("void <init>(clojure.lang.Var,String)"));
//			}
//		else
        emit(C.EXPRESSION, objx, gen);
    }
}
