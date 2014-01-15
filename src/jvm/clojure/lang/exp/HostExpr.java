package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/
public abstract class HostExpr implements Expr, MaybePrimitiveExpr {
    final static Type BOOLEAN_TYPE = Type.getType(Boolean.class);
    final static Type CHAR_TYPE    = Type.getType(Character.class);
    final static Type INTEGER_TYPE = Type.getType(Integer.class);
    final static Type LONG_TYPE    = Type.getType(Long.class);
    final static Type FLOAT_TYPE   = Type.getType(Float.class);
    final static Type DOUBLE_TYPE  = Type.getType(Double.class);
    final static Type SHORT_TYPE   = Type.getType(Short.class);
    final static Type BYTE_TYPE    = Type.getType(Byte.class);
    final static Type NUMBER_TYPE  = Type.getType(Number.class);

    final static Method charValueMethod    = Method.getMethod("char charValue()");
    final static Method booleanValueMethod = Method.getMethod("boolean booleanValue()");

    final static Method charValueOfMethod   = Method.getMethod("Character valueOf(char)");
    final static Method intValueOfMethod    = Method.getMethod("Integer valueOf(int)");
    final static Method longValueOfMethod   = Method.getMethod("Long valueOf(long)");
    final static Method floatValueOfMethod  = Method.getMethod("Float valueOf(float)");
    final static Method doubleValueOfMethod = Method.getMethod("Double valueOf(double)");
    final static Method shortValueOfMethod  = Method.getMethod("Short valueOf(short)");
    final static Method byteValueOfMethod   = Method.getMethod("Byte valueOf(byte)");

    final static Method intValueMethod    = Method.getMethod("int intValue()");
    final static Method longValueMethod   = Method.getMethod("long longValue()");
    final static Method floatValueMethod  = Method.getMethod("float floatValue()");
    final static Method doubleValueMethod = Method.getMethod("double doubleValue()");
    final static Method byteValueMethod   = Method.getMethod("byte byteValue()");
    final static Method shortValueMethod  = Method.getMethod("short shortValue()");

    final static Method fromIntMethod    = Method.getMethod("clojure.lang.Num from(int)");
    final static Method fromLongMethod   = Method.getMethod("clojure.lang.Num from(long)");
    final static Method fromDoubleMethod = Method.getMethod("clojure.lang.Num from(double)");


    //*
    public static void emitBoxReturn(ObjExpr objx, GeneratorAdapter gen, Class returnType) {
        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) {
                Label falseLabel = gen.newLabel();
                Label endLabel = gen.newLabel();
                gen.ifZCmp(GeneratorAdapter.EQ, falseLabel);
                gen.getStatic(Compiler.BOOLEAN_OBJECT_TYPE, "TRUE", Compiler.BOOLEAN_OBJECT_TYPE);
                gen.goTo(endLabel);
                gen.mark(falseLabel);
                gen.getStatic(Compiler.BOOLEAN_OBJECT_TYPE, "FALSE", Compiler.BOOLEAN_OBJECT_TYPE);
//				NIL_EXPR.emit(C.EXPRESSION, fn, gen);
                gen.mark(endLabel);
            } else if (returnType == void.class) {
                Compiler.NIL_EXPR.emit(C.EXPRESSION, objx, gen);
            } else if (returnType == char.class) {
                gen.invokeStatic(CHAR_TYPE, charValueOfMethod);
            } else {
                if (returnType == int.class) {
                    gen.invokeStatic(INTEGER_TYPE, intValueOfMethod);
//						gen.visitInsn(I2L);
//						gen.invokeStatic(NUMBERS_TYPE, Method.getMethod("Number num(long)"));
                } else if (returnType == float.class) {
                    gen.invokeStatic(FLOAT_TYPE, floatValueOfMethod);

//						gen.visitInsn(F2D);
//						gen.invokeStatic(DOUBLE_TYPE, doubleValueOfMethod);
                } else if (returnType == double.class)
                    gen.invokeStatic(DOUBLE_TYPE, doubleValueOfMethod);
                else if (returnType == long.class)
                    gen.invokeStatic(Compiler.NUMBERS_TYPE, Method.getMethod("Number num(long)"));
                else if (returnType == byte.class)
                    gen.invokeStatic(BYTE_TYPE, byteValueOfMethod);
                else if (returnType == short.class)
                    gen.invokeStatic(SHORT_TYPE, shortValueOfMethod);
            }
        }
    }

    //*/
    public static void emitUnboxArg(ObjExpr objx, GeneratorAdapter gen, Class paramType) {
        if (paramType.isPrimitive()) {
            if (paramType == boolean.class) {
                gen.checkCast(BOOLEAN_TYPE);
                gen.invokeVirtual(BOOLEAN_TYPE, booleanValueMethod);
//				Label falseLabel = gen.newLabel();
//				Label endLabel = gen.newLabel();
//				gen.ifNull(falseLabel);
//				gen.push(1);
//				gen.goTo(endLabel);
//				gen.mark(falseLabel);
//				gen.push(0);
//				gen.mark(endLabel);
            } else if (paramType == char.class) {
                gen.checkCast(CHAR_TYPE);
                gen.invokeVirtual(CHAR_TYPE, charValueMethod);
            } else {
                Method m = null;
                gen.checkCast(NUMBER_TYPE);
                if (RT.booleanCast(RT.UNCHECKED_MATH.deref())) {
                    if (paramType == int.class)
                        m = Method.getMethod("int uncheckedIntCast(Object)");
                    else if (paramType == float.class)
                        m = Method.getMethod("float uncheckedFloatCast(Object)");
                    else if (paramType == double.class)
                        m = Method.getMethod("double uncheckedDoubleCast(Object)");
                    else if (paramType == long.class)
                        m = Method.getMethod("long uncheckedLongCast(Object)");
                    else if (paramType == byte.class)
                        m = Method.getMethod("byte uncheckedByteCast(Object)");
                    else if (paramType == short.class)
                        m = Method.getMethod("short uncheckedShortCast(Object)");
                } else {
                    if (paramType == int.class)
                        m = Method.getMethod("int intCast(Object)");
                    else if (paramType == float.class)
                        m = Method.getMethod("float floatCast(Object)");
                    else if (paramType == double.class)
                        m = Method.getMethod("double doubleCast(Object)");
                    else if (paramType == long.class)
                        m = Method.getMethod("long longCast(Object)");
                    else if (paramType == byte.class)
                        m = Method.getMethod("byte byteCast(Object)");
                    else if (paramType == short.class)
                        m = Method.getMethod("short shortCast(Object)");
                }
                gen.invokeStatic(Compiler.RT_TYPE, m);
            }
        } else {
            gen.checkCast(Type.getType(paramType));
        }
    }

    public static class Parser implements IParser {

        /**
         * (. x fieldname-sym) or
         * (. x 0-ary-method)
         * (. x methodname-sym args+)
         * (. x (methodname-sym args?))
         * <p/>
         * Parse the above to expression
         *
         * @param context
         * @param frm
         * @return
         */
        public Expr parse(C context, Object frm) {
            ISeq form = (ISeq) frm;

            if (RT.length(form) < 3)
                throw new IllegalArgumentException("Malformed member expression, expecting (. target member ...)");
            //determine static or instance
            //static target must be symbol, either fully.qualified.Classname or Classname that has been imported
            int line = Compiler.lineDeref();
            int column = Compiler.columnDeref();
            String source = (String) Compiler.SOURCE.deref();
            Class c = maybeClass(RT.second(form), false);
            //at this point c will be non-null if static
            Expr instance = null;
            if (c == null)
                instance = Compiler.analyze(context == C.EVAL ? context : C.EXPRESSION, RT.second(form));

            boolean maybeField = RT.length(form) == 3 && (RT.third(form) instanceof Symbol);

            if (maybeField && !(((Symbol) RT.third(form)).name.charAt(0) == '-')) {
                Symbol sym = (Symbol) RT.third(form);
                if (c != null)
                    maybeField = Reflector.getMethods(c, 0, Compiler.munge(sym.name), true).size() == 0;
                else if (instance != null && instance.hasJavaClass() && instance.getJavaClass() != null)
                    maybeField = Reflector.getMethods(instance.getJavaClass(), 0, Compiler.munge(sym.name), false).size() ==
                            0;
            }

            if (maybeField)    //field
            {
                Symbol sym = (((Symbol) RT.third(form)).name.charAt(0) == '-') ?
                        Symbol.intern(((Symbol) RT.third(form)).name.substring(1))
                        : (Symbol) RT.third(form);
                Symbol tag = Compiler.tagOf(form);
                if (c != null) {
                    return new StaticFieldExpr(line, column, c, Compiler.munge(sym.name), tag);
                } else
                    return new InstanceFieldExpr(line, column, instance, Compiler.munge(sym.name), tag);
            } else {
                ISeq call = (ISeq) ((RT.third(form) instanceof ISeq) ? RT.third(form) : RT.next(RT.next(form)));
                if (!(RT.first(call) instanceof Symbol))
                    throw new IllegalArgumentException("Malformed member expression");
                Symbol sym = (Symbol) RT.first(call);
                Symbol tag = Compiler.tagOf(form);
                PersistentVector args = PersistentVector.EMPTY;
                for (ISeq s = RT.next(call); s != null; s = s.next())
                    args = args.cons(Compiler.analyze(context == C.EVAL ? context : C.EXPRESSION, s.first()));
                if (c != null)
                    return new StaticMethodExpr(source, line, column, tag, c, Compiler.munge(sym.name), args);
                else
                    return new InstanceMethodExpr(source, line, column, tag, instance, Compiler.munge(sym.name), args);
            }
        }
    }

    public static Class maybeClass(Object form, boolean stringOk) {
        if (form instanceof Class)
            return (Class) form;
        Class c = null;
        if (form instanceof Symbol) {
            Symbol sym = (Symbol) form;
            if (sym.ns == null) //if ns-qualified can't be classname
            {
                if (Util.equals(sym, Compiler.COMPILE_STUB_SYM.get()))
                    return (Class) Compiler.COMPILE_STUB_CLASS.get();
                if (sym.name.indexOf('.') > 0 || sym.name.charAt(0) == '[')
                    c = RT.classForName(sym.name);
                else {
                    Object o = Compiler.currentNS().getMapping(sym);
                    if (o instanceof Class)
                        c = (Class) o;
                    else {
                        try {
                            c = RT.classForName(sym.name);
                        } catch (Exception e) {
                            // aargh
                            // leave c set to null -> return null
                        }
                    }
                }
            }
        } else if (stringOk && form instanceof String)
            c = RT.classForName((String) form);
        return c;
    }

    /*
 private static String maybeClassName(Object form, boolean stringOk){
     String className = null;
     if(form instanceof Symbol)
         {
         Symbol sym = (Symbol) form;
         if(sym.ns == null) //if ns-qualified can't be classname
             {
             if(sym.name.indexOf('.') > 0 || sym.name.charAt(0) == '[')
                 className = sym.name;
             else
                 {
                 IPersistentMap imports = (IPersistentMap) ((Var) RT.NS_IMPORTS.get()).get();
                 className = (String) imports.valAt(sym);
                 }
             }
         }
     else if(stringOk && form instanceof String)
         className = (String) form;
     return className;
 }
*/
    public static Class tagToClass(Object tag) {
        Class c = maybeClass(tag, true);
        if (tag instanceof Symbol) {
            Symbol sym = (Symbol) tag;
            if (sym.ns == null) //if ns-qualified can't be classname
            {
                if (sym.name.equals("objects"))
                    c = Object[].class;
                else if (sym.name.equals("ints"))
                    c = int[].class;
                else if (sym.name.equals("longs"))
                    c = long[].class;
                else if (sym.name.equals("floats"))
                    c = float[].class;
                else if (sym.name.equals("doubles"))
                    c = double[].class;
                else if (sym.name.equals("chars"))
                    c = char[].class;
                else if (sym.name.equals("shorts"))
                    c = short[].class;
                else if (sym.name.equals("bytes"))
                    c = byte[].class;
                else if (sym.name.equals("booleans"))
                    c = boolean[].class;
                else if (sym.name.equals("int"))
                    c = Integer.TYPE;
                else if (sym.name.equals("long"))
                    c = Long.TYPE;
                else if (sym.name.equals("float"))
                    c = Float.TYPE;
                else if (sym.name.equals("double"))
                    c = Double.TYPE;
                else if (sym.name.equals("char"))
                    c = Character.TYPE;
                else if (sym.name.equals("short"))
                    c = Short.TYPE;
                else if (sym.name.equals("byte"))
                    c = Byte.TYPE;
                else if (sym.name.equals("boolean"))
                    c = Boolean.TYPE;
            }
        }
        if (c != null)
            return c;
        throw new IllegalArgumentException("Unable to resolve classname: " + tag);
    }
}
