package jdk.internal.template;

import jdk.internal.template.StringTemplateImpl.SharedData;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatException;
import java.lang.invoke.StringConcatFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StringTemplateHelper {

    /**
     * Method type for StringTemplate join MH
     */
    private static final MethodType JOIN_MT = MethodType.methodType(String.class, StringTemplate.class);

    /**
     * Object to string, special casing {@link StringTemplate};
     */
    private static final MethodHandle OBJECT_TO_STRING;

    /**
     * {@link StringTemplate} to string using interpolation.
     */
    private static final MethodHandle TEMPLATE_TO_STRING;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType mt = MethodType.methodType(String.class, Object.class);
            OBJECT_TO_STRING = lookup.findStatic(StringTemplateHelper.class, "objectToString", mt);

            mt = MethodType.methodType(String.class, StringTemplate.class);
            TEMPLATE_TO_STRING = lookup.findStatic(StringTemplateHelper.class, "templateToString", mt);
        } catch(ReflectiveOperationException ex) {
            throw new AssertionError("carrier static init fail", ex);
        }
    }

    static MethodHandle makeJoinMH(MethodType type, List<String> fragments) {
        List<MethodHandle> getters = new ArrayList<>();
        for (int i = 0 ; i < type.parameterCount() ; i++) {
            getters.add(StringTemplateImpl.getter(i, type.parameterType(i)));
        }
        List<Class<?>> ptypes = returnTypes(getters);
        int[] permute = new int[ptypes.size()];
        List<MethodHandle> filters = filterGetters(getters);
        List<Class<?>> ftypes = returnTypes(filters);
        try {
            MethodHandle joinMH = StringConcatFactory.makeConcatWithTemplate(fragments, ftypes);
            joinMH = MethodHandles.filterArguments(joinMH, 0, filters.toArray(MethodHandle[]::new));
            joinMH = MethodHandles.permuteArguments(joinMH, JOIN_MT, permute);
            return joinMH;
        } catch (StringConcatException ex) {
            throw new InternalError("constructing internal string template", ex);
        }
    }

    /**
     * Glean the return types from a list of {@link MethodHandle}.
     *
     * @param mhs  list of {@link MethodHandle}
     * @return list of return types
     */
    private static List<Class<?>> returnTypes(List<MethodHandle> mhs) {
        return mhs.stream()
                .map(mh -> mh.type().returnType())
                .collect(Collectors.toList());
    }

    /**
     * Interpolate nested {@link StringTemplate StringTemplates}.
     * @param getters the string template value getters
     * @return getters filtered to translate {@link StringTemplate StringTemplates} to strings
     */
    private static List<MethodHandle> filterGetters(List<MethodHandle> getters) {
        List<MethodHandle> filters = new ArrayList<>();
        for (MethodHandle getter : getters) {
            Class<?> type = getter.type().returnType();
            if (type == StringTemplate.class) {
                getter = MethodHandles.filterArguments(TEMPLATE_TO_STRING, 0, getter);
            } else if (type == Object.class) {
                getter = MethodHandles.filterArguments(OBJECT_TO_STRING, 0, getter);
            }
            filters.add(getter);
        }
        return filters;
    }

    /**
     * Filter object for {@link StringTemplate} and convert to string, {@link String#valueOf(Object)} otherwise.
     * @param object object to filter
     * @return {@link StringTemplate} interpolation otherwise result of {@link String#valueOf(Object)}.
     */
    private static String objectToString(Object object) {
        if (object instanceof StringTemplate st) {
            return join(st);
        } else {
            return String.valueOf(object);
        }
    }

    /**
     * Filter {@link StringTemplate} to strings.
     * @param st {@link StringTemplate} to filter
     * @return {@link StringTemplate} interpolation otherwise "null"
     */
    private static String templateToString(StringTemplate st) {
        if (st != null) {
            return join(st);
        } else {
            return "null";
        }
    }

    private static String joinSlow(StringTemplate st) {
        StringBuilder buf = new StringBuilder();
        List<String> fragments = st.fragments();
        List<Object> values = st.values();
        buf.append(fragments.get(0));
        for (int i = 0 ; i < values.size() ; i++) {
            buf.append(objectToString(values.get(i)));
            buf.append(fragments.get(i + 1));
        }
        return buf.toString();
    }

    public static String join(StringTemplate st) {
        StringTemplateImpl.SharedData sharedData = ((StringTemplateImpl)st).sharedData;
        MethodHandle joinMH = sharedData.getMetaData(StringTemplateHelper.class,
                () -> StringTemplateHelper.makeJoinMH(sharedData.type(), sharedData.fragments()));
        if (joinMH != null) {
            try {
                return (String)joinMH.invokeExact(st);
            } catch (Throwable ex) {
                throw new InternalError(ex);
            }
        } else {
            return joinSlow(st);
        }
    }

    public static StringTemplate combineST(boolean flatten, StringTemplate... sts) {
        Objects.requireNonNull(sts, "sts must not be null");
        if (sts.length == 0) {
            return new SharedData(List.of(""), List.of(), MethodType.methodType(StringTemplate.class)).makeStringTemplateFromValues();
        } else if (sts.length == 1 && !flatten) {
            return Objects.requireNonNull(sts[0], "string templates should not be null");
        }
        MethodType type = MethodType.methodType(StringTemplate.class);
        List<String> fragments = new ArrayList<>();
        List<StringTemplate.Parameter> parameters = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (StringTemplate st : sts) {
            type = type.appendParameterTypes(((StringTemplateImpl)st).sharedData.type().parameterArray());
            if (flatten) {
                for (int i = 0 ; i < type.parameterCount() ; i++) {
                    if (StringTemplate.class.isAssignableFrom(type.parameterType(i))) {
                        type = type.changeParameterType(i, String.class);
                    }
                }
            }
            Objects.requireNonNull(st, "string templates should not be null");
            flattenST(flatten, st, fragments, parameters, values);
        }
        if (200 < values.size()) {
            throw new RuntimeException("string template combine too many expressions");
        }
        return new SharedData(fragments, parameters, type)
                .makeStringTemplateFromValues(values.toArray());
    }

    public static void flattenST(boolean flatten, StringTemplate st,
                                 List<String> fragments, List<StringTemplate.Parameter> parameters, List<Object> values) {
        Iterator<String> fragmentsIter = st.fragments().iterator();
        if (fragments.isEmpty()) {
            fragments.add(fragmentsIter.next());
        } else {
            int last = fragments.size() - 1;
            fragments.set(last, fragments.get(last) + fragmentsIter.next());
        }
        for (int i = 0 ; i < st.values().size() ; i++) {
            Object value = st.values().get(i);
            StringTemplate.Parameter parameter = st.parameters().get(i);
            if (flatten && value instanceof StringTemplate nested) {
                flattenST(true, nested, fragments, parameters, values);
                int last = fragments.size() - 1;
                fragments.set(last, fragments.get(last) + fragmentsIter.next());
            } else {
                values.add(value);
                parameters.add(parameter);
                fragments.add(fragmentsIter.next());
            }
        }
    }

    private static <Z> List<Z> join(List<Z> one, List<Z> two) {
        return Stream.concat(one.stream(), two.stream()).toList();
    }
}
