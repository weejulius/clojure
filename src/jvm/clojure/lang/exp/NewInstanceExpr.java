package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
* Created by jyu on 14-1-15.
*/
public class NewInstanceExpr extends ObjExpr {
    //IPersistentMap optionsMap = PersistentArrayMap.EMPTY;
    IPersistentCollection methods;

    Map<IPersistentVector, Method>     mmap;
    Map<IPersistentVector, Set<Class>> covariants;

    public NewInstanceExpr(Object tag) {
        super(tag);
    }

    public static class DeftypeParser implements IParser {
        public Expr parse(C context, final Object frm) {
            ISeq rform = (ISeq) frm;
            //(deftype* tagname classname [fields] :implements [interfaces] :tag tagname methods*)
            rform = RT.next(rform);
            String tagname = ((Symbol) rform.first()).toString();
            rform = rform.next();
            Symbol classname = (Symbol) rform.first();
            rform = rform.next();
            IPersistentVector fields = (IPersistentVector) rform.first();
            rform = rform.next();
            IPersistentMap opts = PersistentHashMap.EMPTY;
            while (rform != null && rform.first() instanceof Keyword) {
                opts = opts.assoc(rform.first(), RT.second(rform));
                rform = rform.next().next();
            }

            ObjExpr ret = build((IPersistentVector) RT.get(opts, Compiler.implementsKey, PersistentVector.EMPTY), fields,
                                null, tagname, classname,
                                (Symbol) RT.get(opts, RT.TAG_KEY), rform, frm);
            return ret;
        }
    }

    public static class ReifyParser implements IParser {
        public Expr parse(C context, Object frm) {
            //(reify this-name? [interfaces] (method-name [args] body)*)
            ISeq form = (ISeq) frm;
            ObjMethod enclosingMethod = (ObjMethod) Compiler.METHOD.deref();
            String basename = enclosingMethod != null ?
                    (trimGenID(enclosingMethod.objx.name) + "$")
                    : (Compiler.munge(Compiler.currentNS().name.name) + "$");
            String simpleName = "reify__" + RT.nextID();
            String classname = basename + simpleName;

            ISeq rform = RT.next(form);

            IPersistentVector interfaces = ((IPersistentVector) RT.first(rform)).cons(Symbol.intern(
                    "clojure.lang.IObj"));


            rform = RT.next(rform);


            ObjExpr ret = build(interfaces, null, null, classname, Symbol.intern(classname), null, rform, frm);
            if (frm instanceof IObj && ((IObj) frm).meta() != null)
                return new MetaExpr(ret, MapExpr
                        .parse(context == C.EVAL ? context : C.EXPRESSION, ((IObj) frm).meta()));
            else
                return ret;
        }
    }

    static ObjExpr build(IPersistentVector interfaceSyms, IPersistentVector fieldSyms, Symbol thisSym,
                         String tagName, Symbol className,
                         Symbol typeTag, ISeq methodForms, Object frm) {
        NewInstanceExpr ret = new NewInstanceExpr(null);

        ret.src = frm;
        ret.name = className.toString();
        ret.classMeta = RT.meta(className);
        ret.internalName = ret.name.replace('.', '/');
        ret.objtype = Type.getObjectType(ret.internalName);

        if (thisSym != null)
            ret.thisName = thisSym.name;

        if (fieldSyms != null) {
            IPersistentMap fmap = PersistentHashMap.EMPTY;
            Object[] closesvec = new Object[2 * fieldSyms.count()];
            for (int i = 0; i < fieldSyms.count(); i++) {
                Symbol sym = (Symbol) fieldSyms.nth(i);
                LocalBinding lb = new LocalBinding(-1, sym, null,
                                                   new MethodParamExpr(Compiler.tagClass(Compiler.tagOf(sym))), false, null);
                fmap = fmap.assoc(sym, lb);
                closesvec[i * 2] = lb;
                closesvec[i * 2 + 1] = lb;
            }

            //todo - inject __meta et al into closes - when?
            //use array map to preserve ctor order
            ret.closes = new PersistentArrayMap(closesvec);
            ret.fields = fmap;
            for (int i = fieldSyms.count() - 1; i >= 0 && (((Symbol) fieldSyms.nth(i)).name.equals("__meta") ||
                    ((Symbol) fieldSyms.nth(i)).name.equals("__extmap")); --i)
                ret.altCtorDrops++;
        }
        //todo - set up volatiles
//		ret.volatiles = PersistentHashSet.create(RT.seq(RT.get(ret.optionsMap, volatileKey)));

        PersistentVector interfaces = PersistentVector.EMPTY;
        for (ISeq s = RT.seq(interfaceSyms); s != null; s = s.next()) {
            Class c = (Class) Compiler.resolve((Symbol) s.first());
            if (!c.isInterface())
                throw new IllegalArgumentException("only interfaces are supported, had: " + c.getName());
            interfaces = interfaces.cons(c);
        }
        Class superClass = Object.class;
        Map[] mc = gatherMethods(superClass, RT.seq(interfaces));
        Map overrideables = mc[0];
        Map covariants = mc[1];
        ret.mmap = overrideables;
        ret.covariants = covariants;

        String[] inames = interfaceNames(interfaces);

        Class stub = compileStub(slashname(superClass), ret, inames, frm);
        Symbol thistag = Symbol.intern(null, stub.getName());

        try {
            Var.pushThreadBindings(
                    RT.mapUniqueKeys(Compiler.CONSTANTS, PersistentVector.EMPTY,
                                     Compiler.CONSTANT_IDS, new IdentityHashMap(),
                                     Compiler.KEYWORDS, PersistentHashMap.EMPTY,
                                     Compiler.VARS, PersistentHashMap.EMPTY,
                                     Compiler.KEYWORD_CALLSITES, PersistentVector.EMPTY,
                                     Compiler.PROTOCOL_CALLSITES, PersistentVector.EMPTY,
                                     Compiler.VAR_CALLSITES, Compiler.emptyVarCallSites(),
                                     Compiler.NO_RECUR, null));
            if (ret.isDeftype()) {
                Var.pushThreadBindings(RT.mapUniqueKeys(Compiler.METHOD, null,
                                                        Compiler.LOCAL_ENV, ret.fields
                        , Compiler.COMPILE_STUB_SYM, Symbol.intern(null, tagName)
                        , Compiler.COMPILE_STUB_CLASS, stub));

                ret.hintedFields = RT.subvec(fieldSyms, 0, fieldSyms.count() - ret.altCtorDrops);
            }

            //now (methodname [args] body)*
            ret.line = Compiler.lineDeref();
            ret.column = Compiler.columnDeref();
            IPersistentCollection methods = null;
            for (ISeq s = methodForms; s != null; s = RT.next(s)) {
                NewInstanceMethod m = NewInstanceMethod.parse(ret, (ISeq) RT.first(s), thistag, overrideables);
                methods = RT.conj(methods, m);
            }


            ret.methods = methods;
            ret.keywords = (IPersistentMap) Compiler.KEYWORDS.deref();
            ret.vars = (IPersistentMap) Compiler.VARS.deref();
            ret.constants = (PersistentVector) Compiler.CONSTANTS.deref();
            ret.constantsID = RT.nextID();
            ret.keywordCallsites = (IPersistentVector) Compiler.KEYWORD_CALLSITES.deref();
            ret.protocolCallsites = (IPersistentVector) Compiler.PROTOCOL_CALLSITES.deref();
            ret.varCallsites = (IPersistentSet) Compiler.VAR_CALLSITES.deref();
        } finally {
            if (ret.isDeftype())
                Var.popThreadBindings();
            Var.popThreadBindings();
        }

        try {
            ret.compile(slashname(superClass), inames, false);
        } catch (IOException e) {
            throw Util.sneakyThrow(e);
        }
        ret.getCompiledClass();
        return ret;
    }

    /**
     * Current host interop uses reflection, which requires pre-existing classes
     * Work around this by:
     * Generate a stub class that has the same interfaces and fields as the class we are generating.
     * Use it as a type hint for this, and bind the simple name of the class to this stub (in resolve etc)
     * Unmunge the name (using a magic prefix) on any code gen for classes
     */
    static Class compileStub(String superName, NewInstanceExpr ret, String[] interfaceNames, Object frm) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = cw;
        cv.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, Compiler.COMPILE_STUB_PREFIX + "/" + ret.internalName,
                 null, superName, interfaceNames);

        //instance fields for closed-overs
        for (ISeq s = RT.keys(ret.closes); s != null; s = s.next()) {
            LocalBinding lb = (LocalBinding) s.first();
            int access = Opcodes.ACC_PUBLIC + (ret.isVolatile(lb) ? Opcodes.ACC_VOLATILE :
                    ret.isMutable(lb) ? 0 :
                            Opcodes.ACC_FINAL);
            if (lb.getPrimitiveType() != null)
                cv.visitField(access
                        , lb.name, Type.getType(lb.getPrimitiveType()).getDescriptor(),
                              null, null);
            else
                //todo - when closed-overs are fields, use more specific types here and in ctor and emitLocal?
                cv.visitField(access
                        , lb.name, Compiler.OBJECT_TYPE.getDescriptor(), null, null);
        }

        //ctor that takes closed-overs and does nothing
        org.objectweb.asm.commons.Method m = new org.objectweb.asm.commons.Method("<init>", Type.VOID_TYPE, ret.ctorTypes());
        GeneratorAdapter ctorgen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                                                        m,
                                                        null,
                                                        null,
                                                        cv);
        ctorgen.visitCode();
        ctorgen.loadThis();
        ctorgen.invokeConstructor(Type.getObjectType(superName), voidctor);
        ctorgen.returnValue();
        ctorgen.endMethod();

        if (ret.altCtorDrops > 0) {
            Type[] ctorTypes = ret.ctorTypes();
            Type[] altCtorTypes = new Type[ctorTypes.length - ret.altCtorDrops];
            for (int i = 0; i < altCtorTypes.length; i++)
                altCtorTypes[i] = ctorTypes[i];
            org.objectweb.asm.commons.Method alt = new org.objectweb.asm.commons.Method("<init>", Type.VOID_TYPE, altCtorTypes);
            ctorgen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                                           alt,
                                           null,
                                           null,
                                           cv);
            ctorgen.visitCode();
            ctorgen.loadThis();
            ctorgen.loadArgs();
            for (int i = 0; i < ret.altCtorDrops; i++)
                ctorgen.visitInsn(Opcodes.ACONST_NULL);

            ctorgen.invokeConstructor(Type.getObjectType(Compiler.COMPILE_STUB_PREFIX + "/" + ret.internalName),
                                      new org.objectweb.asm.commons.Method("<init>", Type.VOID_TYPE, ctorTypes));

            ctorgen.returnValue();
            ctorgen.endMethod();
        }
        //end of class
        cv.visitEnd();

        byte[] bytecode = cw.toByteArray();
        DynamicClassLoader loader = (DynamicClassLoader) Compiler.LOADER.deref();
        return loader.defineClass(Compiler.COMPILE_STUB_PREFIX + "." + ret.name, bytecode, frm);
    }

    static String[] interfaceNames(IPersistentVector interfaces) {
        int icnt = interfaces.count();
        String[] inames = icnt > 0 ? new String[icnt] : null;
        for (int i = 0; i < icnt; i++)
            inames[i] = slashname((Class) interfaces.nth(i));
        return inames;
    }


    static String slashname(Class c) {
        return c.getName().replace('.', '/');
    }

    protected void emitStatics(ClassVisitor cv) {
        if (this.isDeftype()) {
            //getBasis()
            org.objectweb.asm.commons.Method meth = org.objectweb.asm.commons.Method.getMethod(
                    "clojure.lang.IPersistentVector getBasis()");
            GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                                                        meth,
                                                        null,
                                                        null,
                                                        cv);
            emitValue(hintedFields, gen);
            gen.returnValue();
            gen.endMethod();

            if (this.isDeftype() && this.fields.count() > this.hintedFields.count()) {
                //create(IPersistentMap)
                String className = name.replace('.', '/');
                int i = 1;
                int fieldCount = hintedFields.count();

                MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "create",
                                                  "(Lclojure/lang/IPersistentMap;)L" + className + ";", null, null);
                mv.visitCode();

                for (ISeq s = RT.seq(hintedFields); s != null; s = s.next(), i++) {
                    String bName = ((Symbol) s.first()).name;
                    Class k = Compiler.tagClass(Compiler.tagOf(s.first()));

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitLdcInsn(bName);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "clojure/lang/Keyword", "intern",
                                       "(Ljava/lang/String;)Lclojure/lang/Keyword;");
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "clojure/lang/IPersistentMap", "valAt",
                                       "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
                    if (k.isPrimitive()) {
                        mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(Compiler.boxClass(k)).getInternalName());
                    }
                    mv.visitVarInsn(Opcodes.ASTORE, i);
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitLdcInsn(bName);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "clojure/lang/Keyword", "intern",
                                       "(Ljava/lang/String;)Lclojure/lang/Keyword;");
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "clojure/lang/IPersistentMap", "without",
                                       "(Ljava/lang/Object;)Lclojure/lang/IPersistentMap;");
                    mv.visitVarInsn(Opcodes.ASTORE, 0);
                }

                mv.visitTypeInsn(Opcodes.NEW, className);
                mv.visitInsn(Opcodes.DUP);

                org.objectweb.asm.commons.Method ctor = new org.objectweb.asm.commons.Method("<init>", Type.VOID_TYPE, ctorTypes());

                if (hintedFields.count() > 0)
                    for (i = 1; i <= fieldCount; i++) {
                        mv.visitVarInsn(Opcodes.ALOAD, i);
                        Class k = Compiler.tagClass(Compiler.tagOf(hintedFields.nth(i - 1)));
                        if (k.isPrimitive()) {
                            String b = Type.getType(Compiler.boxClass(k)).getInternalName();
                            String p = Type.getType(k).getDescriptor();
                            String n = k.getName();

                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, b, n + "Value", "()" + p);
                        }
                    }

                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "clojure/lang/RT", "seqOrElse",
                                   "(Ljava/lang/Object;)Ljava/lang/Object;");
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>", ctor.getDescriptor());
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(4 + fieldCount, 1 + fieldCount);
                mv.visitEnd();
            }
        }
    }

    protected void emitMethods(ClassVisitor cv) {
        for (ISeq s = RT.seq(methods); s != null; s = s.next()) {
            ObjMethod method = (ObjMethod) s.first();
            method.emit(this, cv);
        }
        //emit bridge methods
        for (Map.Entry<IPersistentVector, Set<Class>> e : covariants.entrySet()) {
            Method m = mmap.get(e.getKey());
            Class[] params = m.getParameterTypes();
            Type[] argTypes = new Type[params.length];

            for (int i = 0; i < params.length; i++) {
                argTypes[i] = Type.getType(params[i]);
            }

            org.objectweb.asm.commons.Method target = new org.objectweb.asm.commons.Method(m.getName(), Type.getType(m.getReturnType()), argTypes);

            for (Class retType : e.getValue()) {
                org.objectweb.asm.commons.Method meth = new org.objectweb.asm.commons.Method(m.getName(), Type.getType(retType), argTypes);

                GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC + Opcodes.ACC_BRIDGE,
                                                            meth,
                                                            null,
                                                            //todo don't hardwire this
                                                            Compiler.EXCEPTION_TYPES,
                                                            cv);
                gen.visitCode();
                gen.loadThis();
                gen.loadArgs();
                gen.invokeInterface(Type.getType(m.getDeclaringClass()), target);
                gen.returnValue();
                gen.endMethod();
            }
        }
    }

    static public IPersistentVector msig(Method m) {
        return RT.vector(m.getName(), RT.seq(m.getParameterTypes()), m.getReturnType());
    }

    static void considerMethod(Method m, Map mm) {
        IPersistentVector mk = msig(m);
        int mods = m.getModifiers();

        if (!(mm.containsKey(mk)
                || !(Modifier.isPublic(mods) || Modifier.isProtected(mods))
                || Modifier.isStatic(mods)
                || Modifier.isFinal(mods))) {
            mm.put(mk, m);
        }
    }

    static void gatherMethods(Class c, Map mm) {
        for (; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods())
                considerMethod(m, mm);
            for (Method m : c.getMethods())
                considerMethod(m, mm);
        }
    }

    static public Map[] gatherMethods(Class sc, ISeq interfaces) {
        Map allm = new HashMap();
        gatherMethods(sc, allm);
        for (; interfaces != null; interfaces = interfaces.next())
            gatherMethods((Class) interfaces.first(), allm);

        Map<IPersistentVector, Method> mm =
                new HashMap<IPersistentVector, Method>();
        Map<IPersistentVector, Set<Class>> covariants = new HashMap<IPersistentVector, Set<Class>>();
        for (Object o : allm.entrySet()) {
            Map.Entry e = (Map.Entry) o;
            IPersistentVector mk = (IPersistentVector) e.getKey();
            mk = (IPersistentVector) mk.pop();
            Method m = (Method) e.getValue();
            if (mm.containsKey(mk)) //covariant return
            {
                Set<Class> cvs = covariants.get(mk);
                if (cvs == null) {
                    cvs = new HashSet<Class>();
                    covariants.put(mk, cvs);
                }
                Method om = mm.get(mk);
                if (om.getReturnType().isAssignableFrom(m.getReturnType())) {
                    cvs.add(om.getReturnType());
                    mm.put(mk, m);
                } else
                    cvs.add(m.getReturnType());
            } else
                mm.put(mk, m);
        }
        return new Map[]{mm, covariants};
    }
}
