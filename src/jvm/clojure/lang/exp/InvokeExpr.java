package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import clojure.lang.Compiler.CompilerException;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.List;

/**
* Created by jyu on 14-1-15.
*/
public class InvokeExpr implements Expr {
    public final Expr              fexpr;
    public final Object            tag;
    public final IPersistentVector args;
    public final int               line;
    public final int               column;
    public final String            source;
    public boolean isProtocol = false;
    public boolean isDirect   = false;
    public int     siteIndex  = -1;
    public Class                    protocolOn;
    public java.lang.reflect.Method onMethod;
    static Keyword onKey        = Keyword.intern("on");
    static Keyword methodMapKey = Keyword.intern("method-map");

    public InvokeExpr(String source, int line, int column, Symbol tag, Expr fexpr, IPersistentVector args) {
        this.source = source;
        this.fexpr = fexpr;
        this.args = args;
        this.line = line;
        this.column = column;
        if (fexpr instanceof VarExpr) {
            Var fvar = ((VarExpr) fexpr).var;
            Var pvar = (Var) RT.get(fvar.meta(), Compiler.protocolKey);
            if (pvar != null && Compiler.PROTOCOL_CALLSITES.isBound()) {
                this.isProtocol = true;
                this.siteIndex = Compiler.registerProtocolCallsite(((VarExpr) fexpr).var);
                Object pon = RT.get(pvar.get(), onKey);
                this.protocolOn = HostExpr.maybeClass(pon, false);
                if (this.protocolOn != null) {
                    IPersistentMap mmap = (IPersistentMap) RT.get(pvar.get(), methodMapKey);
                    Keyword mmapVal = (Keyword) mmap.valAt(Keyword.intern(fvar.sym));
                    if (mmapVal == null) {
                        throw new IllegalArgumentException(
                                "No method of interface: " + protocolOn.getName() +
                                        " found for function: " + fvar.sym + " of protocol: " + pvar.sym +
                                        " (The protocol method may have been defined before and removed.)");
                    }
                    String mname = Compiler.munge(mmapVal.sym.toString());
                    List methods = Reflector.getMethods(protocolOn, args.count() - 1, mname, false);
                    if (methods.size() != 1) {
                        StringBuilder sb = new StringBuilder("No single method: " + mname + " of interface: " +
                                                                     protocolOn.getName() +
                                                                     " found for function: " + fvar.sym +
                                                                     " of protocol: " + pvar.sym);

                        sb.append("\nThe size of the methods is:").append(methods.size() + "\n");
                        for (Object method : methods) {
                            sb.append("  - ")
                                    .append(method.toString())
                                    .append("\n");
                        }
                        throw new IllegalArgumentException(sb.toString());
                    }

                    this.onMethod = (java.lang.reflect.Method) methods.get(0);
                }
            }
        }

        if (tag != null) {
            this.tag = tag;
        } else if (fexpr instanceof VarExpr) {
            Object arglists = RT.get(RT.meta(((VarExpr) fexpr).var), Compiler.arglistsKey);
            Object sigTag = null;
            for (ISeq s = RT.seq(arglists); s != null; s = s.next()) {
                APersistentVector sig = (APersistentVector) s.first();
                int restOffset = sig.indexOf(Compiler._AMP_);
                if (args.count() == sig.count() || (restOffset > -1 && args.count() >= restOffset)) {
                    sigTag = Compiler.tagOf(sig);
                    break;
                }
            }

            this.tag = sigTag == null ? ((VarExpr) fexpr).tag : sigTag;
        } else {
            this.tag = null;
        }
    }

    public Object eval() {
        try {
            IFn fn = (IFn) fexpr.eval();
            PersistentVector argvs = PersistentVector.EMPTY;
            for (int i = 0; i < args.count(); i++)
                argvs = argvs.cons(((Expr) args.nth(i)).eval());
            return fn.applyTo(RT.seq(Util.ret1(argvs, argvs = null)));
        } catch (Throwable e) {
            if (!(e instanceof CompilerException))
                throw new CompilerException(source, line, column, e);
            else
                throw (CompilerException) e;
        }
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        gen.visitLineNumber(line, gen.mark());
        if (isProtocol) {
            emitProto(context, objx, gen);
        } else {
            fexpr.emit(C.EXPRESSION, objx, gen);
            gen.checkCast(Compiler.IFN_TYPE);
            emitArgsAndCall(0, context, objx, gen);
        }
        if (context == C.STATEMENT)
            gen.pop();
    }

    public void emitProto(C context, ObjExpr objx, GeneratorAdapter gen) {
        Label onLabel = gen.newLabel();
        Label callLabel = gen.newLabel();
        Label endLabel = gen.newLabel();

        Var v = ((VarExpr) fexpr).var;

        Expr e = (Expr) args.nth(0);
        e.emit(C.EXPRESSION, objx, gen);
        gen.dup(); //target, target
        gen.invokeStatic(Compiler.UTIL_TYPE, Method.getMethod("Class classOf(Object)")); //target,class
        gen.getStatic(objx.objtype, objx.cachedClassName(siteIndex), Compiler.CLASS_TYPE); //target,class,cached-class
        gen.visitJumpInsn(Opcodes.IF_ACMPEQ, callLabel); //target
        if (protocolOn != null) {
            gen.dup(); //target, target
            gen.instanceOf(Type.getType(protocolOn));
            gen.ifZCmp(GeneratorAdapter.NE, onLabel);
        }

        gen.dup(); //target, target
        gen.invokeStatic(Compiler.UTIL_TYPE, Method.getMethod("Class classOf(Object)")); //target,class
        gen.putStatic(objx.objtype, objx.cachedClassName(siteIndex), Compiler.CLASS_TYPE); //target

        gen.mark(callLabel); //target
        objx.emitVar(gen, v);
        gen.invokeVirtual(Compiler.VAR_TYPE, Method.getMethod("Object getRawRoot()")); //target, proto-fn
        gen.swap();
        emitArgsAndCall(1, context, objx, gen);
        gen.goTo(endLabel);

        gen.mark(onLabel); //target
        if (protocolOn != null) {
            MethodExpr.emitTypedArgs(objx, gen, onMethod.getParameterTypes(), RT.subvec(args, 1, args.count()));
            if (context == C.RETURN) {
                ObjMethod method = (ObjMethod) Compiler.METHOD.deref();
                method.emitClearLocals(gen);
            }
            Method m = new Method(onMethod.getName(), Type.getReturnType(onMethod), Type.getArgumentTypes(
                    onMethod));
            gen.invokeInterface(Type.getType(protocolOn), m);
            HostExpr.emitBoxReturn(objx, gen, onMethod.getReturnType());
        }
        gen.mark(endLabel);
    }

    void emitArgsAndCall(int firstArgToEmit, C context, ObjExpr objx, GeneratorAdapter gen) {
        for (int i = firstArgToEmit; i < Math.min(Compiler.MAX_POSITIONAL_ARITY, args.count()); i++) {
            Expr e = (Expr) args.nth(i);
            e.emit(C.EXPRESSION, objx, gen);
        }
        if (args.count() > Compiler.MAX_POSITIONAL_ARITY) {
            PersistentVector restArgs = PersistentVector.EMPTY;
            for (int i = Compiler.MAX_POSITIONAL_ARITY; i < args.count(); i++) {
                restArgs = restArgs.cons(args.nth(i));
            }
            MethodExpr.emitArgsAsArray(restArgs, objx, gen);
        }

        if (context == C.RETURN) {
            ObjMethod method = (ObjMethod) Compiler.METHOD.deref();
            method.emitClearLocals(gen);
        }

        gen.invokeInterface(Compiler.IFN_TYPE, new Method("invoke", Compiler.OBJECT_TYPE, Compiler.ARG_TYPES[Math.min(
                Compiler.MAX_POSITIONAL_ARITY + 1,
                                                                                           args.count())]));
    }

    public boolean hasJavaClass() {
        return tag != null;
    }

    public Class getJavaClass() {
        return HostExpr.tagToClass(tag);
    }

    static public Expr parse(C context, ISeq form) {
        if (context != C.EVAL)
            context = C.EXPRESSION;
        Expr fexpr = Compiler.analyze(context, form.first());
        if (fexpr instanceof VarExpr && ((VarExpr) fexpr).var.equals(Compiler.INSTANCE) && RT.count(form) == 3) {
            Expr sexpr = Compiler.analyze(C.EXPRESSION, RT.second(form));
            if (sexpr instanceof ConstantExpr) {
                Object val = ((ConstantExpr) sexpr).val();
                if (val instanceof Class) {
                    return new InstanceOfExpr((Class) val, Compiler.analyze(context, RT.third(form)));
                }
            }
        }

//		if(fexpr instanceof VarExpr && context != C.EVAL)
//			{
//			Var v = ((VarExpr)fexpr).var;
//			if(RT.booleanCast(RT.get(RT.meta(v),staticKey)))
//				{
//				return StaticInvokeExpr.parse(v, RT.next(form), tagOf(form));
//				}
//			}

        if (fexpr instanceof VarExpr && context != C.EVAL) {
            Var v = ((VarExpr) fexpr).var;
            Object arglists = RT.get(RT.meta(v), Compiler.arglistsKey);
            int arity = RT.count(form.next());
            for (ISeq s = RT.seq(arglists); s != null; s = s.next()) {
                IPersistentVector args = (IPersistentVector) s.first();
                if (args.count() == arity) {
                    String primc = FnMethod.primInterface(args);
                    if (primc != null)
                        return Compiler.analyze(context,
                                                RT.listStar(Symbol.intern(".invokePrim"),
                                                            ((Symbol) form.first()).withMeta(RT.map(RT.TAG_KEY,
                                                                                                    Symbol.intern(
                                                                                                            primc))),
                                                            form.next()));
                    break;
                }
            }
        }

        if (fexpr instanceof KeywordExpr && RT.count(form) == 2 && Compiler.KEYWORD_CALLSITES.isBound()) {
//			fexpr = new ConstantExpr(new KeywordCallSite(((KeywordExpr)fexpr).k));
            Expr target = Compiler.analyze(context, RT.second(form));
            return new KeywordInvokeExpr((String) Compiler.SOURCE.deref(), Compiler.lineDeref(), Compiler.columnDeref(), Compiler
                    .tagOf(form),
                                         (KeywordExpr) fexpr, target);
        }
        PersistentVector args = PersistentVector.EMPTY;
        for (ISeq s = RT.seq(form.next()); s != null; s = s.next()) {
            args = args.cons(Compiler.analyze(context, s.first()));
        }
//		if(args.count() > MAX_POSITIONAL_ARITY)
//			throw new IllegalArgumentException(
//					String.format("No more than %d args supported", MAX_POSITIONAL_ARITY));

        return new InvokeExpr((String) Compiler.SOURCE.deref(), Compiler.lineDeref(), Compiler.columnDeref(), Compiler.tagOf(
                form), fexpr, args);
    }
}
